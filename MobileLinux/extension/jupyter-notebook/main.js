/**
 * Jupyter Notebook Extension for MobileLinux Code IDE
 * Inspired by VS Code Jupyter & Interactive Python
 * 
 * Features:
 * - Run # %% Interactive Python cells directly via Ubuntu PRoot Python
 * - Interactive Output Drawer with execution status and timings
 * - Start / Stop local Jupyter Server in Terminal
 * - One-tap cell insertion (+ Cell)
 * - Convert .ipynb notebook JSON to Python with cells, and vice-versa
 * - Keyboard shortcuts (Ctrl+Enter, Ctrl+Shift+Enter)
 */

class JupyterNotebookExtension {
    constructor() {
        this.ide = null;
        this.toolbarEl = null;
        this.outputDockEl = null;
        this.isExecuting = false;
    }

    init(ide) {
        this.ide = ide;

        // 1. Register Editor Keyboard Commands
        ide.editor.addCommand({
            name: "jupyterRunCell",
            bindKey: { win: "Ctrl-Enter", mac: "Command-Enter" },
            exec: () => this.runCurrentCell()
        });

        ide.editor.addCommand({
            name: "jupyterInsertCell",
            bindKey: { win: "Ctrl-Shift-Enter", mac: "Command-Shift-Enter" },
            exec: () => this.insertCell()
        });

        // 2. Add Selection Action Bar Buttons
        ide.ui.addToolbarButton({
            id: "jupyter-run-cell",
            label: "▶ Run Cell",
            tooltip: "Execute # %% code cell",
            onClick: () => this.runCurrentCell()
        });

        ide.ui.addToolbarButton({
            id: "jupyter-add-cell",
            label: "+ Cell",
            tooltip: "Insert # %% cell separator",
            onClick: () => this.insertCell()
        });

        // 3. Inject Floating Jupyter Toolbar
        this.createFloatingToolbar();

        ide.ui.showToast("🪐 Jupyter Notebook extension ready! Use # %% for cells.");
    }

    createFloatingToolbar() {
        if (document.getElementById("jupyter-toolbar")) return;

        const bar = document.createElement("div");
        bar.id = "jupyter-toolbar";
        bar.innerHTML = `
            <div class="jupyter-logo-badge" title="Jupyter Notebook (Click to toggle)" id="jupyter-toggle-btn">🪐</div>
            <button class="jupyter-btn jupyter-btn-primary" id="jupyter-run-btn" title="Run Current # %% Cell (Ctrl+Enter)">
                ▶ <span class="jupyter-btn-label">Run Cell</span>
            </button>
            <button class="jupyter-btn jupyter-extra-btn" id="jupyter-insert-btn" title="Insert New Cell (+)">
                + <span class="jupyter-btn-label">Cell</span>
            </button>
            <button class="jupyter-btn jupyter-btn-orange jupyter-extra-btn" id="jupyter-server-btn" title="Start Jupyter Server">
                <span class="jupyter-btn-label">Server</span>
            </button>
            <button class="jupyter-btn jupyter-extra-btn" id="jupyter-convert-btn" title="Convert .ipynb to Python">
                📖 <span class="jupyter-btn-label">.ipynb</span>
            </button>
        `;

        document.body.appendChild(bar);
        this.toolbarEl = bar;

        // Event Listeners
        const toggleBtn = bar.querySelector("#jupyter-toggle-btn");
        const runBtn = bar.querySelector("#jupyter-run-btn");
        const insertBtn = bar.querySelector("#jupyter-insert-btn");
        const serverBtn = bar.querySelector("#jupyter-server-btn");
        const convertBtn = bar.querySelector("#jupyter-convert-btn");

        toggleBtn.addEventListener("click", () => {
            bar.classList.toggle("minimized");
        });

        runBtn.addEventListener("click", () => this.runCurrentCell());
        insertBtn.addEventListener("click", () => this.insertCell());
        serverBtn.addEventListener("click", () => this.startJupyterServer());
        convertBtn.addEventListener("click", () => this.handleNotebookConversion());
    }

    /**
     * Finds the code cell (# %%) surrounding the current cursor line
     */
    getCurrentCell() {
        const content = this.ide.editor.getValue() || "";
        const lines = content.split("\n");
        const cursor = this.ide.editor.getCursor() || { row: 0, column: 0 };
        const cursorRow = Math.min(cursor.row, lines.length - 1);

        let startRow = 0;
        let endRow = lines.length;

        // Search backward for cell start (# %%)
        for (let i = cursorRow; i >= 0; i--) {
            if (lines[i].trim().startsWith("# %%")) {
                startRow = i;
                break;
            }
        }

        // Search forward for next cell start (# %%)
        for (let i = cursorRow + 1; i < lines.length; i++) {
            if (lines[i].trim().startsWith("# %%")) {
                endRow = i;
                break;
            }
        }

        // Extract cell lines, skipping the marker itself
        const cellLines = [];
        for (let i = startRow; i < endRow; i++) {
            const line = lines[i];
            if (i === startRow && line.trim().startsWith("# %%")) {
                continue; // Skip the marker header
            }
            cellLines.push(line);
        }

        const cellCode = cellLines.join("\n").trim();
        return {
            startRow: startRow,
            endRow: endRow,
            code: cellCode,
            hasMarker: lines[startRow].trim().startsWith("# %%")
        };
    }

    /**
     * Executes the cell in Ubuntu PRoot Python and streams output
     */
    runCurrentCell() {
        if (this.isExecuting) {
            this.ide.ui.showToast("⏳ Kernel is busy executing...");
            return;
        }

        const cell = this.getCurrentCell();
        if (!cell.code || cell.code.length === 0) {
            this.ide.ui.showToast("Current cell is empty. Add python code to run.");
            return;
        }

        this.isExecuting = true;
        const startTime = Date.now();
        this.showOutputDock("Running cell...", "running");

        // Execute code via python3 inside Ubuntu PRoot
        const escapedCode = cell.code;
        const runCmd = `python3 -u -c ${JSON.stringify(escapedCode)}`;

        this.ide.terminal.exec(runCmd, (exitCode, stdout, stderr) => {
            this.isExecuting = false;
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);

            let statusText = exitCode === 0 ? `SUCCESS [${elapsed}s]` : `FAILED (${exitCode}) [${elapsed}s]`;
            let statusClass = exitCode === 0 ? "success" : "error";

            let outputHtml = "";
            if (stdout && stdout.trim().length > 0) {
                outputHtml += `<div class="jupyter-out-stdout">${this.escapeHtml(stdout.trim())}</div>`;
            }
            if (stderr && stderr.trim().length > 0) {
                outputHtml += `<div class="jupyter-out-stderr">${this.escapeHtml(stderr.trim())}</div>`;
            }
            if (!outputHtml) {
                outputHtml = `<div class="jupyter-out-info">[Execution finished with no text output]</div>`;
            }

            this.showOutputDock(outputHtml, statusClass, statusText);
        });
    }

    /**
     * Displays or updates the floating/docked Jupyter kernel output drawer
     */
    showOutputDock(contentHtml, statusClass = "success", statusLabel = "Ready") {
        let dock = document.getElementById("jupyter-output-dock");
        if (!dock) {
            dock = document.createElement("div");
            dock.id = "jupyter-output-dock";
            document.body.appendChild(dock);
        }

        dock.innerHTML = `
            <div class="jupyter-dock-header">
                <div class="jupyter-dock-title">
                    <span>🪐 Jupyter Output</span>
                    <span class="jupyter-status-tag jupyter-status-${statusClass}">${statusLabel}</span>
                </div>
                <button class="jupyter-dock-close" id="jupyter-dock-close-btn" title="Close">✕</button>
            </div>
            <div class="jupyter-dock-body" id="jupyter-dock-body">
                ${contentHtml}
            </div>
        `;

        dock.querySelector("#jupyter-dock-close-btn").addEventListener("click", () => {
            dock.remove();
        });

        // Scroll output to bottom
        const bodyEl = dock.querySelector("#jupyter-dock-body");
        if (bodyEl) {
            bodyEl.scrollTop = bodyEl.scrollHeight;
        }

        this.outputDockEl = dock;
    }

    /**
     * Inserts a new # %% cell marker at cursor
     */
    insertCell() {
        const cursor = this.ide.editor.getCursor() || { row: 0, column: 0 };
        const cellTemplate = "\n# %% [Cell]\n# Enter your python code here:\n";
        this.ide.editor.insert(cellTemplate);
        this.ide.ui.showToast("➕ New Jupyter cell inserted");
    }

    /**
     * Dispatches server launch command to the active Terminal tab
     */
    startJupyterServer() {
        this.ide.terminal.sendToConsole("jupyter notebook --ip=127.0.0.1 --port=8888 --no-browser --allow-root");
        this.ide.ui.showToast("🚀 Starting Jupyter Server on http://localhost:8888! Open in Dev Browser.");
    }

    /**
     * Converts .ipynb JSON file to Python interactive script, or exports Python with cells to .ipynb
     */
    handleNotebookConversion() {
        const text = this.ide.editor.getValue() || "";
        const isJsonNotebook = text.trim().startsWith("{") && text.includes('"cells"');

        if (isJsonNotebook) {
            try {
                const nb = JSON.parse(text);
                if (!Array.isArray(nb.cells)) {
                    throw new Error("Invalid notebook structure");
                }

                let pyOutput = "# %% [Jupyter Notebook Converted]\n# MobileLinux Interactive Python\n\n";
                for (const cell of nb.cells) {
                    const cellType = cell.cell_type || "code";
                    const source = Array.isArray(cell.source) ? cell.source.join("") : (cell.source || "");

                    if (cellType === "markdown") {
                        pyOutput += `# %% [markdown]\n"""\n${source}\n"""\n\n`;
                    } else {
                        pyOutput += `# %% [code]\n${source}\n\n`;
                    }
                }

                this.ide.editor.setValue(pyOutput.trimEnd() + "\n");
                this.ide.ui.showToast("📖 Notebook converted to Python cells successfully!");
            } catch (e) {
                this.ide.ui.showToast("Failed to parse notebook JSON: " + e.message);
            }
        } else {
            // Export current python code with # %% to .ipynb JSON format
            try {
                const lines = text.split("\n");
                const cells = [];
                let currentCellLines = [];
                let currentCellType = "code";

                for (const line of lines) {
                    if (line.trim().startsWith("# %%")) {
                        if (currentCellLines.length > 0) {
                            cells.push({
                                cell_type: currentCellType,
                                metadata: {},
                                execution_count: null,
                                source: currentCellLines.join("\n").split(/(?<=\n)/),
                                outputs: []
                            });
                            currentCellLines = [];
                        }
                        currentCellType = line.includes("markdown") ? "markdown" : "code";
                    } else {
                        currentCellLines.push(line);
                    }
                }

                if (currentCellLines.length > 0) {
                    cells.push({
                        cell_type: currentCellType,
                        metadata: {},
                        execution_count: null,
                        source: currentCellLines.join("\n").split(/(?<=\n)/),
                        outputs: []
                    });
                }

                const nbJson = {
                    cells: cells,
                    metadata: {
                        kernelspec: {
                            display_name: "Python 3 (MobileLinux)",
                            language: "python",
                            name: "python3"
                        },
                        language_info: {
                            name: "python",
                            version: "3.10"
                        }
                    },
                    nbformat: 4,
                    nbformat_minor: 5
                };

                this.ide.editor.setValue(JSON.stringify(nbJson, null, 2));
                this.ide.ui.showToast("💾 Exported to Jupyter Notebook (.ipynb JSON) format!");
            } catch (e) {
                this.ide.ui.showToast("Export error: " + e.message);
            }
        }
    }

    escapeHtml(str) {
        return str
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    destroy() {
        if (this.toolbarEl) {
            this.toolbarEl.remove();
            this.toolbarEl = null;
        }
        if (this.outputDockEl) {
            this.outputDockEl.remove();
            this.outputDockEl = null;
        }
        this.ide.ui.removeToolbarButton("jupyter-run-cell");
        this.ide.ui.removeToolbarButton("jupyter-add-cell");
    }
}

module.exports = new JupyterNotebookExtension();
