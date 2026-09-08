/* MobileLinux Ultimate Mobile UX for Jupyter Notebook 7 & JupyterLab */
(function() {
    if (window.__ml_touch_init) return;
    window.__ml_touch_init = true;

    // 1. Polyfill window.open to bypass mobile popup blockers and navigate in-tab or via WebChromeClient
    var origOpen = window.open;
    window.open = function(url, target, features) {
        if (!url || url === '' || url === 'about:blank') {
            var fakeWin = {
                opener: null,
                location: {
                    set href(val) { if (val && val !== 'about:blank') window.location.href = val; },
                    get href() { return window.location.href; }
                },
                focus: function() {},
                close: function() {}
            };
            return fakeWin;
        }
        try {
            var w = origOpen ? origOpen.call(window, url, target, features) : null;
            if (!w) { window.location.href = url; }
            return w;
        } catch(e) {
            window.location.href = url;
            return null;
        }
    };

    // SVG Vector Icons (Strictly No Emojis)
    var SVG_PLAY = '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>';
    var SVG_CHECK = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
    var SVG_SPINNER = '<svg class="ml-spin" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><circle cx="12" cy="12" r="9" stroke-dasharray="32" stroke-dashoffset="10"/></svg>';

    var SVG_COPY = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-right:4px;"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>';
    var SVG_PASTE = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-right:4px;"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"></path><rect x="8" y="2" width="8" height="4" rx="1" ry="1"></rect></svg>';
    var SVG_UNDO = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7v6h6"></path><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"></path></svg>';
    var SVG_REDO = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 7v6h-6"></path><path d="M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3L21 13"></path></svg>';
    var SVG_ARROW_LEFT = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>';
    var SVG_ARROW_RIGHT = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>';

    var SVG_RUN = '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="vertical-align:-1px;margin-right:4px;"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>';
    var SVG_NEXT = '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="vertical-align:-1px;margin-right:4px;"><polygon points="4 4 14 12 4 20 4 4"></polygon><line x1="18" y1="4" x2="18" y2="20" stroke="currentColor" stroke-width="3"></line></svg>';
    var SVG_PLUS = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-1px;margin-right:4px;"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>';
    var SVG_STOP = '<svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor" style="vertical-align:-1px;margin-right:4px;"><rect x="4" y="4" width="16" height="16" rx="2"></rect></svg>';
    var SVG_RESTART = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-1px;margin-right:4px;"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>';
    var SVG_KEYS = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-right:4px;"><rect x="2" y="4" width="20" height="16" rx="2" ry="2"></rect><line x1="6" y1="8" x2="6" y2="8.01"></line><line x1="10" y1="8" x2="10" y2="8.01"></line><line x1="14" y1="8" x2="14" y2="8.01"></line><line x1="18" y1="8" x2="18" y2="8.01"></line><line x1="6" y1="12" x2="6" y2="12.01"></line><line x1="18" y1="12" x2="18" y2="12.01"></line><line x1="7" y1="16" x2="17" y2="16"></line></svg>';
    var SVG_COLLAPSE = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>';
    var SVG_EXPAND = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>';

    // Helper: Execute a command via JupyterLab / Notebook 7 Lumino command registry
    function runJupyterCmd(cmd, args) {
        var app = window.jupyterapp || window.jupyterlab;
        if (app && app.commands) {
            try {
                return app.commands.execute(cmd, args);
            } catch(e) {
                console.warn('[MobileLinux] Command failed:', cmd, e);
            }
        }
        return null;
    }

    // Helper: Get currently active / focused cell
    function getActiveCell() {
        return document.querySelector('.jp-Cell.jp-mod-active') ||
               document.querySelector('.cell.selected') ||
               document.querySelector('.jp-Cell.jp-mod-selected') ||
               document.querySelector('.jp-CodeCell:focus-within') ||
               document.querySelector('.jp-Cell:focus-within') ||
               document.querySelector('.jp-Cell');
    }

    // Helper: Execute active or specific cell with visual feedback
    function executeCell(cellElem, btnElem) {
        if (!cellElem) cellElem = getActiveCell();
        if (cellElem) {
            document.querySelectorAll('.jp-Cell.jp-mod-active, .cell.selected').forEach(function(c) {
                if (c !== cellElem) c.classList.remove('jp-mod-active', 'selected');
            });
            cellElem.classList.add('jp-mod-active');
            var prompt = cellElem.querySelector('.jp-InputPrompt') || cellElem;
            try {
                prompt.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
            } catch(e) {}
        }

        if (window.getSelection) {
            try { window.getSelection().removeAllRanges(); } catch(e) {}
        }

        if (btnElem) {
            btnElem.classList.add('ml-running');
            btnElem.classList.remove('ml-success');
            btnElem.innerHTML = SVG_SPINNER;
        }

        var executed = false;
        var app = window.jupyterapp || window.jupyterlab;
        if (app && app.commands) {
            try {
                app.commands.execute('notebook:run-cell');
                executed = true;
            } catch(e) {
                try {
                    app.commands.execute('notebook:run-cell-and-select-next');
                    executed = true;
                } catch(e2) {}
            }
        }

        if (!executed) {
            var tbRun = document.querySelector('button[data-command="notebook:run-cell"]') ||
                        document.querySelector('button[data-command="notebook:run-cell-and-select-next"]') ||
                        document.querySelector('.jp-ToolbarButtonComponent[data-command*="run"]') ||
                        document.querySelector('button[title*="Run this cell"]') ||
                        document.querySelector('button[title*="run the selected cells"]') ||
                        document.querySelector('#run_int') ||
                        document.querySelector('button[data-jupyter-action*="run"]');
            if (tbRun) {
                tbRun.click();
                executed = true;
            }
        }

        if (!executed) {
            var target = document.activeElement || (cellElem && cellElem.querySelector('.cm-content')) || document;
            try {
                target.dispatchEvent(new KeyboardEvent('keydown', {
                    key: 'Enter',
                    code: 'Enter',
                    keyCode: 13,
                    which: 13,
                    shiftKey: true,
                    bubbles: true,
                    cancelable: true
                }));
            } catch(e) {}
        }

        if (btnElem && cellElem) {
            var checkCount = 0;
            var interval = setInterval(function() {
                checkCount++;
                var promptText = (cellElem.querySelector('.jp-InputPrompt') || cellElem.querySelector('.prompt.input_prompt') || {}).textContent || '';
                var isRunning = promptText.indexOf('*') !== -1 || cellElem.classList.contains('jp-mod-running');
                if (!isRunning || checkCount > 50) {
                    clearInterval(interval);
                    btnElem.classList.remove('ml-running');
                    btnElem.classList.add('ml-success');
                    btnElem.innerHTML = SVG_CHECK;
                    setTimeout(function() {
                        btnElem.classList.remove('ml-success');
                        btnElem.innerHTML = SVG_PLAY;
                    }, 1200);
                }
            }, 300);
        }
    }

    // Insert text at cursor in active CodeMirror editor (for virtual keys & paste)
    function insertCodeText(text, offsetBack) {
        if (!text) return false;
        var cell = getActiveCell();
        var editorElem = (cell && cell.querySelector('.cm-content')) ||
                         (document.activeElement && document.activeElement.classList && document.activeElement.classList.contains('cm-content') ? document.activeElement : null) ||
                         document.querySelector('.jp-Cell.jp-mod-active .cm-content') ||
                         document.querySelector('.cm-content:focus') ||
                         document.querySelector('.cm-content');

        if (editorElem) {
            // 1. CodeMirror 6 direct view dispatch (JupyterLab 4 / Notebook 7)
            var cmView = editorElem.cmView && editorElem.cmView.view;
            if (cmView) {
                try {
                    cmView.focus();
                    var mainSel = cmView.state.selection.main;
                    var insertLen = text.length;
                    var anchorPos = mainSel.from + insertLen - (offsetBack || 0);
                    cmView.dispatch({
                        changes: { from: mainSel.from, to: mainSel.to, insert: text },
                        selection: { anchor: Math.max(mainSel.from, anchorPos) },
                        scrollIntoView: true
                    });
                    return true;
                } catch(e) {
                    console.warn('[MobileLinux] cmView dispatch failed:', e);
                }
            }

            // 2. Jupyter CodeMirrorEditor instance
            var host = editorElem.closest('.cm-editor') || editorElem.closest('.jp-Editor');
            if (host && host.editor && typeof host.editor.replaceSelection === 'function') {
                try {
                    host.editor.focus();
                    host.editor.replaceSelection(text);
                    return true;
                } catch(e) {}
            }

            // 3. CodeMirror 5 instance (Legacy Jupyter Notebook)
            var cm5 = editorElem.CodeMirror || (host && host.CodeMirror);
            if (cm5 && typeof cm5.replaceSelection === 'function') {
                try {
                    cm5.focus();
                    cm5.replaceSelection(text);
                    return true;
                } catch(e) {}
            }
        }

        // 4. Input / Textarea fallback
        var activeEl = document.activeElement;
        if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA')) {
            try {
                var s = activeEl.selectionStart || 0;
                var en = activeEl.selectionEnd || 0;
                var v = activeEl.value;
                activeEl.value = v.substring(0, s) + text + v.substring(en);
                var newPos = s + text.length - (offsetBack || 0);
                activeEl.selectionStart = activeEl.selectionEnd = newPos;
                activeEl.dispatchEvent(new Event('input', { bubbles: true }));
                return true;
            } catch(e) {}
        }

        // 5. Fallback execCommand
        try {
            if (editorElem) editorElem.focus();
            if (document.execCommand('insertText', false, text)) {
                return true;
            }
        } catch(e) {}

        return false;
    }

    // 2. Colab-Style Per-Cell Play Button Injection (Persistent across cell runs)
    function attachPlayButtons() {
        var p = window.location.pathname || '';
        var isNotebookPage = p.indexOf('/notebooks/') !== -1 || p.indexOf('/lab') !== -1 || document.querySelector('.jp-Notebook') !== null;
        if (!isNotebookPage) return;

        var cells = document.querySelectorAll('.jp-Cell, .jp-CodeCell, .cell.code_cell');
        for (var i = 0; i < cells.length; i++) {
            var cell = cells[i];
            if (cell.querySelector('.ml-cell-play-btn')) continue;

            var promptElem = cell.querySelector('.jp-InputPrompt') || cell.querySelector('.prompt.input_prompt');
            var inputArea = cell.querySelector('.jp-InputArea') || cell.querySelector('.input_area') || cell.querySelector('.jp-Cell-inputWrapper');

            if (!promptElem && !inputArea) continue;

            var btn = document.createElement('div');
            btn.className = 'ml-cell-play-btn';
            btn.title = 'Run cell';
            btn.innerHTML = SVG_PLAY;

            (function(c, b) {
                function onRun(ev) {
                    ev.preventDefault();
                    ev.stopPropagation();
                    executeCell(c, b);
                }
                b.addEventListener('click', onRun);
                b.addEventListener('touchend', onRun);
            })(cell, btn);

            // Mount above editor border, aligned with prompt row
            var host = inputArea || cell;
            host.appendChild(btn);
        }
    }

    // Dynamic MutationObserver for notebook cells & prompt updates
    var nbObserver = null;
    function observeNotebookMutations() {
        if (nbObserver) return;
        var target = document.querySelector('.jp-Notebook') || document.querySelector('#notebook-container') || document.body;
        if (!target) return;
        try {
            nbObserver = new MutationObserver(function() {
                attachPlayButtons();
            });
            nbObserver.observe(target, { childList: true, subtree: true });
        } catch(e) {}
    }

    // Keyboard-adaptive viewport tracking for floating toolbar
    function updateFloatingToolbarPosition() {
        var tb = document.getElementById('ml-floating-toolbar');
        if (!tb) return;
        if (window.visualViewport) {
            var keyboardOffset = Math.max(0, window.innerHeight - window.visualViewport.height - window.visualViewport.offsetTop);
            tb.style.bottom = Math.max(16, keyboardOffset + 12) + 'px';
            var ks = document.getElementById('ml-keys-strip');
            if (ks) ks.style.bottom = Math.max(65, keyboardOffset + 58) + 'px';
        }
    }
    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', updateFloatingToolbarPosition);
        window.visualViewport.addEventListener('scroll', updateFloatingToolbarPosition);
    }

    // Track active selection text for reliable copy even after button touch
    var lastSelectedText = '';
    var lastCopiedType = ''; // 'text' or 'cell'
    document.addEventListener('selectionchange', function() {
        var sel = window.getSelection && window.getSelection();
        if (sel && sel.toString() && sel.toString().trim()) {
            lastSelectedText = sel.toString();
        }
    });

    function copySelectedCode() {
        var text = '';
        var sel = window.getSelection && window.getSelection();
        if (sel && sel.toString() && sel.toString().trim()) {
            text = sel.toString();
        } else if (lastSelectedText && lastSelectedText.trim()) {
            text = lastSelectedText;
        }

        var isCellCopy = false;
        var cell = getActiveCell();

        // If no specific text selection, copy the cell and its entire content!
        if (!text && cell) {
            isCellCopy = true;
            // 1. Try JupyterLab command to copy cell to Jupyter internal clipboard
            runJupyterCmd('notebook:copy-cell');

            // 2. Also extract text content of the cell editor
            var cm = cell.querySelector('.cm-content');
            if (cm && cm.cmView && cm.cmView.view) {
                try {
                    text = cm.cmView.view.state.doc.toString();
                } catch(e) {}
            }
            if (!text && cm) {
                text = cm.innerText || cm.textContent || '';
            }
        }

        if (text) {
            window.__ml_clipboard = text;
            lastCopiedType = isCellCopy ? 'cell' : 'text';
            window.__ml_copied_type = lastCopiedType;

            if (window.MobileLinuxClipboard && window.MobileLinuxClipboard.copyText) {
                try { window.MobileLinuxClipboard.copyText(text); } catch(e) {}
            }
            if (navigator.clipboard && navigator.clipboard.writeText) {
                try { navigator.clipboard.writeText(text); } catch(e) {}
            }
            try { document.execCommand('copy'); } catch(e) {}
        }

        var btn = document.getElementById('ml-k-copy');
        if (btn) {
            var span = btn.querySelector('span');
            if (span) span.textContent = isCellCopy ? 'Cell Copied' : 'Copied';
            btn.classList.add('ml-k-done');
            setTimeout(function() {
                if (span) span.textContent = 'Copy';
                btn.classList.remove('ml-k-done');
            }, 1000);
        }
    }

    function pasteCode() {
        var textToPaste = '';

        // 1. First check Native Android Clipboard Bridge
        if (window.MobileLinuxClipboard && window.MobileLinuxClipboard.pasteText) {
            try {
                var sys = window.MobileLinuxClipboard.pasteText();
                if (sys && sys.trim()) {
                    textToPaste = sys;
                }
            } catch(e) {}
        }

        // 2. Fallback to in-page memory clipboard
        if (!textToPaste && window.__ml_clipboard) {
            textToPaste = window.__ml_clipboard;
        }

        var cell = getActiveCell();
        var editorElem = cell && cell.querySelector('.cm-content');
        var isEditorActive = editorElem && (editorElem === document.activeElement || (cell && cell.contains(document.activeElement)));

        var pasted = false;

        // If user previously copied a cell and is not actively typing inside code box,
        // try Jupyter's native notebook:paste-cell-below command first
        if (window.__ml_copied_type === 'cell' && !isEditorActive) {
            try {
                if (runJupyterCmd('notebook:paste-cell-below')) {
                    pasted = true;
                }
            } catch(e) {}
        }

        // If not pasted as a cell, paste text into active cell's editor!
        if (!pasted && textToPaste) {
            pasted = insertCodeText(textToPaste);
        }

        // Fallback for asynchronous navigator.clipboard or cell paste
        if (!pasted) {
            if (navigator.clipboard && navigator.clipboard.readText) {
                navigator.clipboard.readText().then(function(t) {
                    if (t) {
                        insertCodeText(t);
                    } else {
                        runJupyterCmd('notebook:paste-cell-below');
                    }
                }).catch(function() {
                    runJupyterCmd('notebook:paste-cell-below');
                });
            } else {
                runJupyterCmd('notebook:paste-cell-below');
            }
        }

        var btn = document.getElementById('ml-k-paste');
        if (btn) {
            var span = btn.querySelector('span');
            if (span) span.textContent = 'Pasted';
            btn.classList.add('ml-k-done');
            setTimeout(function() {
                if (span) span.textContent = 'Paste';
                btn.classList.remove('ml-k-done');
            }, 800);
        }
    }

    function moveCursor(dir) {
        var sel = window.getSelection && window.getSelection();
        if (sel && sel.modify) {
            try {
                sel.modify('move', dir === 'left' ? 'backward' : 'forward', 'character');
                return;
            } catch(e) {}
        }
        var cell = getActiveCell();
        var target = (cell && cell.querySelector('.cm-content')) || document.activeElement;
        if (target) {
            var key = dir === 'left' ? 'ArrowLeft' : 'ArrowRight';
            var code = dir === 'left' ? 37 : 39;
            target.dispatchEvent(new KeyboardEvent('keydown', { key: key, code: key, keyCode: code, which: code, bubbles: true }));
        }
    }

    // 3. Floating Mobile Action Dock (Run, Add, Stop, Restart, Keys)
    function injectFloatingToolbar() {
        if (document.getElementById('ml-floating-toolbar')) return;
        if (!document.body) return;

        var p = window.location.pathname || '';
        var isNotebookPage = p.indexOf('/notebooks/') !== -1 || p.indexOf('/lab') !== -1 || document.querySelector('.jp-Notebook') !== null;
        if (!isNotebookPage) return;

        // Create Keyboard Strip with Rich Keys (No Emojis)
        var keyStrip = document.createElement('div');
        keyStrip.id = 'ml-keys-strip';
        keyStrip.innerHTML = [
            '<button class="ml-key-btn ml-k-action" id="ml-k-copy" title="Copy Selected Text">' + SVG_COPY + '<span>Copy</span></button>',
            '<button class="ml-key-btn ml-k-action" id="ml-k-paste" title="Paste Code">' + SVG_PASTE + '<span>Paste</span></button>',
            '<button class="ml-key-btn ml-k-tool" id="ml-k-undo" title="Undo">' + SVG_UNDO + '</button>',
            '<button class="ml-key-btn ml-k-tool" id="ml-k-redo" title="Redo">' + SVG_REDO + '</button>',
            '<button class="ml-key-btn ml-k-nav" id="ml-k-left" title="Move Cursor Left">' + SVG_ARROW_LEFT + '</button>',
            '<button class="ml-key-btn ml-k-nav" id="ml-k-right" title="Move Cursor Right">' + SVG_ARROW_RIGHT + '</button>',
            '<button class="ml-key-btn" id="ml-k-tab" title="Indent 4 spaces">Tab</button>',
            '<button class="ml-key-btn" id="ml-k-untab" title="Dedent / Unindent">Untab</button>',
            '<button class="ml-key-btn ml-k-esc" id="ml-k-esc" title="Command Mode">Esc</button>',
            '<button class="ml-key-btn" id="ml-k-colon">:</button>',
            '<button class="ml-key-btn" id="ml-k-equal">=</button>',
            '<button class="ml-key-btn" id="ml-k-paren">( )</button>',
            '<button class="ml-key-btn" id="ml-k-bracket">[ ]</button>',
            '<button class="ml-key-btn" id="ml-k-brace">{ }</button>',
            '<button class="ml-key-btn" id="ml-k-quote">" "</button>',
            '<button class="ml-key-btn" id="ml-k-squote">\' \'</button>',
            '<button class="ml-key-btn" id="ml-k-plus">+</button>',
            '<button class="ml-key-btn" id="ml-k-minus">-</button>',
            '<button class="ml-key-btn" id="ml-k-star">*</button>',
            '<button class="ml-key-btn" id="ml-k-slash">/</button>',
            '<button class="ml-key-btn" id="ml-k-percent">%</button>',
            '<button class="ml-key-btn" id="ml-k-lt">&lt;</button>',
            '<button class="ml-key-btn" id="ml-k-gt">&gt;</button>',
            '<button class="ml-key-btn" id="ml-k-comma">,</button>',
            '<button class="ml-key-btn" id="ml-k-dot">.</button>',
            '<button class="ml-key-btn" id="ml-k-under">_</button>',
            '<button class="ml-key-btn" id="ml-k-hash">#</button>',
            '<button class="ml-key-btn" id="ml-k-excl">!</button>',
            '<button class="ml-key-btn" id="ml-k-quest">?</button>',
            '<button class="ml-key-btn" id="ml-k-pipe">|</button>',
            '<button class="ml-key-btn" id="ml-k-amp">&amp;</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-def">def</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-import">import</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-return">return</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-if">if</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-for">for</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-in">in</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-print">print()</button>',
            '<button class="ml-key-btn ml-k-code" id="ml-k-len">len()</button>'
        ].join('');
        document.body.appendChild(keyStrip);

        // Prevent virtual keys from stealing editor focus or collapsing selection
        keyStrip.querySelectorAll('.ml-key-btn').forEach(function(b) {
            b.addEventListener('mousedown', function(e) { e.preventDefault(); });
            b.addEventListener('pointerdown', function(e) { e.preventDefault(); });
        });

        // Bind virtual key actions
        document.getElementById('ml-k-copy').addEventListener('click', function(e) { e.preventDefault(); copySelectedCode(); });
        document.getElementById('ml-k-paste').addEventListener('click', function(e) { e.preventDefault(); pasteCode(); });
        document.getElementById('ml-k-undo').addEventListener('click', function(e) { e.preventDefault(); runJupyterCmd('notebook:undo-cell-action') || document.execCommand('undo'); });
        document.getElementById('ml-k-redo').addEventListener('click', function(e) { e.preventDefault(); runJupyterCmd('notebook:redo-cell-action') || document.execCommand('redo'); });
        document.getElementById('ml-k-left').addEventListener('click', function(e) { e.preventDefault(); moveCursor('left'); });
        document.getElementById('ml-k-right').addEventListener('click', function(e) { e.preventDefault(); moveCursor('right'); });
        document.getElementById('ml-k-tab').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('    '); });
        document.getElementById('ml-k-untab').addEventListener('click', function(e) { e.preventDefault(); runJupyterCmd('notebook:outdent') || runJupyterCmd('notebook:dedent'); });
        document.getElementById('ml-k-esc').addEventListener('click', function(e) {
            e.preventDefault();
            var target = document.activeElement || document;
            target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true }));
        });
        document.getElementById('ml-k-colon').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(':'); });
        document.getElementById('ml-k-equal').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' = '); });
        document.getElementById('ml-k-paren').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('()', 1); });
        document.getElementById('ml-k-bracket').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('[]', 1); });
        document.getElementById('ml-k-brace').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('{}', 1); });
        document.getElementById('ml-k-quote').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('""', 1); });
        document.getElementById('ml-k-squote').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('\'\'', 1); });
        document.getElementById('ml-k-plus').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' + '); });
        document.getElementById('ml-k-minus').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' - '); });
        document.getElementById('ml-k-star').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('*'); });
        document.getElementById('ml-k-slash').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('/'); });
        document.getElementById('ml-k-percent').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('%'); });
        document.getElementById('ml-k-lt').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' < '); });
        document.getElementById('ml-k-gt').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' > '); });
        document.getElementById('ml-k-comma').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(', '); });
        document.getElementById('ml-k-dot').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('.'); });
        document.getElementById('ml-k-under').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('_'); });
        document.getElementById('ml-k-hash').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('# '); });
        document.getElementById('ml-k-excl').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('!'); });
        document.getElementById('ml-k-quest').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('?'); });
        document.getElementById('ml-k-pipe').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' | '); });
        document.getElementById('ml-k-amp').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' & '); });
        document.getElementById('ml-k-def').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('def '); });
        document.getElementById('ml-k-import').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('import '); });
        document.getElementById('ml-k-return').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('return '); });
        document.getElementById('ml-k-if').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('if '); });
        document.getElementById('ml-k-for').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('for '); });
        document.getElementById('ml-k-in').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' in '); });
        document.getElementById('ml-k-print').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('print()', 1); });
        document.getElementById('ml-k-len').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('len()', 1); });

        // Create Toolbar (No Emojis)
        var bar = document.createElement('div');
        bar.id = 'ml-floating-toolbar';
        bar.innerHTML = [
            '<div id="ml-bar-inner">',
            '  <button class="ml-bar-btn ml-btn-run" id="ml-action-run">' + SVG_RUN + '<span>Run</span></button>',
            '  <button class="ml-bar-btn" id="ml-action-next">' + SVG_NEXT + '<span>Next</span></button>',
            '  <button class="ml-bar-btn ml-btn-add" id="ml-action-add-code">' + SVG_PLUS + '<span>Code</span></button>',
            '  <button class="ml-bar-btn ml-btn-add" id="ml-action-add-text">' + SVG_PLUS + '<span>Text</span></button>',
            '  <button class="ml-bar-btn ml-btn-stop" id="ml-action-stop">' + SVG_STOP + '<span>Stop</span></button>',
            '  <button class="ml-bar-btn" id="ml-action-restart">' + SVG_RESTART + '<span>Restart</span></button>',
            '  <button class="ml-bar-btn ml-btn-toggle" id="ml-action-keys">' + SVG_KEYS + '<span>Keys</span></button>',
            '</div>',
            '<button class="ml-bar-btn ml-btn-collapse" id="ml-action-collapse" title="Collapse / Expand Toolbar">' + SVG_COLLAPSE + '</button>'
        ].join('');
        document.body.appendChild(bar);

        // Bind Toolbar actions
        document.getElementById('ml-action-run').addEventListener('click', function(e) {
            e.preventDefault();
            executeCell(getActiveCell(), null);
        });

        document.getElementById('ml-action-next').addEventListener('click', function(e) {
            e.preventDefault();
            if (!runJupyterCmd('notebook:run-cell-and-select-next')) {
                executeCell(getActiveCell(), null);
            }
        });

        document.getElementById('ml-action-add-code').addEventListener('click', function(e) {
            e.preventDefault();
            if (!runJupyterCmd('notebook:insert-cell-below')) {
                var btn = document.querySelector('button[data-command="notebook:insert-cell-below"]') ||
                          document.querySelector('button[title*="Insert a cell below"]');
                if (btn) btn.click();
            }
            setTimeout(attachPlayButtons, 200);
        });

        document.getElementById('ml-action-add-text').addEventListener('click', function(e) {
            e.preventDefault();
            if (runJupyterCmd('notebook:insert-cell-below')) {
                setTimeout(function() {
                    runJupyterCmd('notebook:change-cell-to-markdown');
                }, 100);
            } else {
                var btn = document.querySelector('button[data-command="notebook:insert-cell-below"]');
                if (btn) btn.click();
            }
            setTimeout(attachPlayButtons, 200);
        });

        document.getElementById('ml-action-stop').addEventListener('click', function(e) {
            e.preventDefault();
            if (!runJupyterCmd('notebook:interrupt-kernel')) {
                var btn = document.querySelector('button[data-command="notebook:interrupt-kernel"]') ||
                          document.querySelector('button[title*="Interrupt the kernel"]');
                if (btn) btn.click();
            }
        });

        document.getElementById('ml-action-restart').addEventListener('click', function(e) {
            e.preventDefault();
            if (!runJupyterCmd('notebook:restart-kernel')) {
                var btn = document.querySelector('button[data-command="notebook:restart-kernel"]') ||
                          document.querySelector('button[title*="Restart the kernel"]');
                if (btn) btn.click();
            }
        });

        document.getElementById('ml-action-keys').addEventListener('click', function(e) {
            e.preventDefault();
            var ks = document.getElementById('ml-keys-strip');
            if (ks) {
                ks.classList.toggle('ml-visible');
            }
        });

        document.getElementById('ml-action-collapse').addEventListener('click', function(e) {
            e.preventDefault();
            var inner = document.getElementById('ml-bar-inner');
            var collapseBtn = document.getElementById('ml-action-collapse');
            if (inner) {
                var isCollapsed = inner.classList.toggle('ml-collapsed');
                if (collapseBtn) {
                    collapseBtn.innerHTML = isCollapsed ? SVG_EXPAND : SVG_COLLAPSE;
                }
            }
        });

        updateFloatingToolbarPosition();
    }

    // 4. Fallback API functions for Tree / Dashboard
    function getXsrfToken() {
        var match = document.cookie.match('\\b_xsrf=([^;]+)');
        return match ? match[1] : '';
    }

    function getCurrentDirectory() {
        var p = window.location.pathname || '';
        var treeIdx = p.indexOf('/tree');
        if (treeIdx !== -1) {
            var sub = p.substring(treeIdx + 5).replace(/^\/+/, '');
            return sub ? decodeURIComponent(sub) : '';
        }
        return '';
    }

    function createNotebookFallback() {
        var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';
        if (!base.endsWith('/')) base += '/';
        var dir = getCurrentDirectory();
        var url = base + 'api/contents/' + (dir ? encodeURI(dir) : '');
        var headers = { 'Content-Type': 'application/json' };
        var xsrf = getXsrfToken();
        if (xsrf) headers['X-XSRFToken'] = xsrf;

        fetch(url, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ type: 'notebook' })
        })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data && data.path) {
                window.location.href = base + 'notebooks/' + encodeURI(data.path);
            }
        })
        .catch(function(err) {
            console.error('[MobileLinux] Failed to create notebook via API:', err);
        });
    }

    function createFolderFallback() {
        var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';
        if (!base.endsWith('/')) base += '/';
        var dir = getCurrentDirectory();
        var url = base + 'api/contents/' + (dir ? encodeURI(dir) : '');
        var headers = { 'Content-Type': 'application/json' };
        var xsrf = getXsrfToken();
        if (xsrf) headers['X-XSRFToken'] = xsrf;

        fetch(url, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ type: 'directory' })
        })
        .then(function() {
            if (window.jupyterapp && window.jupyterapp.commands) {
                window.jupyterapp.commands.execute('filebrowser:create-new-directory');
            } else {
                window.location.reload();
            }
        })
        .catch(function(err) {
            console.error('[MobileLinux] Failed to create folder via API:', err);
        });
    }

    function createFileFallback() {
        var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';
        if (!base.endsWith('/')) base += '/';
        var dir = getCurrentDirectory();
        var url = base + 'api/contents/' + (dir ? encodeURI(dir) : '');
        var headers = { 'Content-Type': 'application/json' };
        var xsrf = getXsrfToken();
        if (xsrf) headers['X-XSRFToken'] = xsrf;

        fetch(url, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ type: 'file' })
        })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data && data.path) {
                window.location.href = base + 'edit/' + encodeURI(data.path);
            }
        })
        .catch(function(err) {
            console.error('[MobileLinux] Failed to create file via API:', err);
        });
    }

    // 5. Minimal Vertical Mini-Dock on Bottom-Right for /tree or /
    function addMobileDashboardToolbar() {
        if (document.getElementById('ml-vertical-dock')) return;
        if (!document.body) return;
        var p = window.location.pathname || '';
        if (p.indexOf('/tree') === -1 && p !== '/' && !p.endsWith('/')) return;

        var dock = document.createElement('div');
        dock.id = 'ml-vertical-dock';
        dock.innerHTML = [
            '<button class="ml-vdock-btn" id="ml-action-new-nb" title="New Notebook">',
            '  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#f37626" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">',
            '    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>',
            '    <polyline points="14 2 14 8 20 8"></polyline>',
            '    <line x1="12" y1="18" x2="12" y2="12"></line>',
            '    <line x1="9" y1="15" x2="15" y2="15"></line>',
            '  </svg>',
            '</button>',
            '<button class="ml-vdock-btn" id="ml-action-new-folder" title="New Folder">',
            '  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#4b5563" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">',
            '    <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>',
            '    <line x1="12" y1="11" x2="12" y2="17"></line>',
            '    <line x1="9" y1="14" x2="15" y2="14"></line>',
            '  </svg>',
            '</button>',
            '<button class="ml-vdock-btn" id="ml-action-open-lab" title="JupyterLab">',
            '  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#4b5563" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">',
            '    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>',
            '  </svg>',
            '</button>',
            '<button class="ml-vdock-btn" id="ml-action-refresh" title="Reload Page">',
            '  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#4b5563" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">',
            '    <polyline points="23 4 23 10 17 10"></polyline>',
            '    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>',
            '  </svg>',
            '</button>'
        ].join('');
        document.body.appendChild(dock);

        document.getElementById('ml-action-new-nb').addEventListener('click', function(ev) {
            ev.preventDefault(); ev.stopPropagation();
            if (!runJupyterCmd('notebook:create-new', { isLauncher: true }) &&
                !runJupyterCmd('filebrowser:create-new-notebook')) {
                createNotebookFallback();
            }
        });

        document.getElementById('ml-action-new-folder').addEventListener('click', function(ev) {
            ev.preventDefault(); ev.stopPropagation();
            if (!runJupyterCmd('filebrowser:create-new-directory')) {
                createFolderFallback();
            }
        });

        document.getElementById('ml-action-open-lab').addEventListener('click', function(ev) {
            ev.preventDefault(); ev.stopPropagation();
            var base = (window.jupyterConfigData && window.jupyterConfigData.baseUrl) || '/';
            if (!base.endsWith('/')) base += '/';
            window.location.href = base + 'lab';
        });

        document.getElementById('ml-action-refresh').addEventListener('click', function(ev) {
            ev.preventDefault(); ev.stopPropagation();
            window.location.reload();
        });
    }

    // Helper to find Lumino Widget instance from DOM node
    function getLuminoWidget(node) {
        if (!node) return null;
        return node.__widget__ || node._widget || node.__luminoWidget || node.widget || null;
    }

    // 6. Direct Touch Fix for Lumino Dropdown Menus, MenuBars, File Listing & Toolbars (CRITICAL)
    // Suppress unwanted cell context menu inside editor on touch
    document.addEventListener('contextmenu', function(e) {
        if (e.target && e.target.closest('.jp-InputArea, .cm-editor, .cm-content, .jp-Cell-inputWrapper, .jp-Cell')) {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            return false;
        }
    }, { capture: true });

    var lastTouchX = 0;
    var lastTouchY = 0;
    var lastTouchTime = 0;

    document.addEventListener('touchstart', function(e) {
        if (e.touches && e.touches.length === 1) {
            lastTouchX = e.touches[0].clientX;
            lastTouchY = e.touches[0].clientY;
            lastTouchTime = Date.now();
        }

        var menuItem = e.target.closest('.lm-Menu-item');
        if (menuItem) {
            var rect = menuItem.getBoundingClientRect();
            var cx = rect.left + rect.width / 2;
            var cy = rect.top + rect.height / 2;

            var menuNode = menuItem.closest('.lm-Menu');
            var w = getLuminoWidget(menuNode);
            if (w) {
                var items = Array.prototype.slice.call(menuNode.querySelectorAll('.lm-Menu-item'));
                var idx = items.indexOf(menuItem);
                if (idx !== -1) {
                    w.activeIndex = idx;
                }
            }

            menuItem.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, view: window }));
            menuItem.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, button: 0, view: window }));
            menuItem.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, button: 0, view: window }));
        }

        var menuBarItem = e.target.closest('.lm-MenuBar-item');
        if (menuBarItem) {
            var rectM = menuBarItem.getBoundingClientRect();
            var cxM = rectM.left + rectM.width / 2;
            var cyM = rectM.top + rectM.height / 2;
            var barNode = menuBarItem.closest('.lm-MenuBar');
            var bw = getLuminoWidget(barNode);
            if (bw) {
                var bItems = Array.prototype.slice.call(barNode.querySelectorAll('.lm-MenuBar-item'));
                var bIdx = bItems.indexOf(menuBarItem);
                if (bIdx !== -1) {
                    bw.activeIndex = bIdx;
                }
            }
            menuBarItem.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, cancelable: true, clientX: cxM, clientY: cyM, button: 0, view: window }));
        }
    }, { capture: true, passive: true });

    document.addEventListener('touchend', function(e) {
        var target = e.target;
        if (!target) return;

        // A. Handle Lumino Dropdown Menu Items
        var menuItem = target.closest('.lm-Menu-item');
        if (menuItem) {
            var hasSubmenu = menuItem.classList.contains('lm-mod-has-submenu') || menuItem.querySelector('.lm-Menu-itemSubmenuIcon') !== null;
            var menuNode = menuItem.closest('.lm-Menu');
            var w = getLuminoWidget(menuNode);

            if (w) {
                var items = Array.prototype.slice.call(menuNode.querySelectorAll('.lm-Menu-item'));
                var idx = items.indexOf(menuItem);
                if (idx !== -1) {
                    w.activeIndex = idx;
                    if (typeof w.triggerActiveItem === 'function') {
                        e.preventDefault();
                        e.stopPropagation();
                        w.triggerActiveItem();
                        return;
                    }
                }
            }

            if (hasSubmenu) {
                return; // Let Lumino naturally open the submenu
            }

            e.preventDefault();
            e.stopPropagation();

            var rect = menuItem.getBoundingClientRect();
            var cx = rect.left + rect.width / 2;
            var cy = rect.top + rect.height / 2;

            menuItem.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy }));
            menuItem.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window, button: 0, clientX: cx, clientY: cy }));
            menuItem.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window, button: 0, clientX: cx, clientY: cy }));
            menuItem.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy }));

            var labelElem = menuItem.querySelector('.lm-Menu-itemLabel');
            var text = (labelElem ? labelElem.textContent : menuItem.textContent || '').trim().toLowerCase();
            var cmd = menuItem.getAttribute('data-command');

            if (cmd) {
                runJupyterCmd(cmd);
            } else if (text.indexOf('python 3') !== -1 || text.indexOf('ipykernel') !== -1 || text === 'notebook') {
                if (!runJupyterCmd('notebook:create-new', { isLauncher: true }) &&
                    !runJupyterCmd('filebrowser:create-new-notebook')) {
                    createNotebookFallback();
                }
            } else if (text.indexOf('folder') !== -1) {
                if (!runJupyterCmd('filebrowser:create-new-directory')) {
                    createFolderFallback();
                }
            } else if (text.indexOf('terminal') !== -1) {
                runJupyterCmd('terminal:create-new');
            } else if (text.indexOf('console') !== -1) {
                runJupyterCmd('console:create');
            } else if (text.indexOf('file') !== -1) {
                if (!runJupyterCmd('filebrowser:create-new-file')) {
                    createFileFallback();
                }
            }

            if (w && typeof w.close === 'function') {
                w.close();
            }
            return;
        }

        // B. Handle Lumino MenuBar Item Click (File, View, Settings, Help)
        var mbItem = target.closest('.lm-MenuBar-item');
        if (mbItem) {
            var rectM = mbItem.getBoundingClientRect();
            var cxM = rectM.left + rectM.width / 2;
            var cyM = rectM.top + rectM.height / 2;
            var barNode = mbItem.closest('.lm-MenuBar');
            var bw = getLuminoWidget(barNode);
            if (bw) {
                var bItems = Array.prototype.slice.call(barNode.querySelectorAll('.lm-MenuBar-item'));
                var bIdx = bItems.indexOf(mbItem);
                if (bIdx !== -1) {
                    bw.activeIndex = bIdx;
                    if (typeof bw.openActiveMenu === 'function') {
                        bw.openActiveMenu();
                    }
                }
            }
            mbItem.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window, button: 0, clientX: cxM, clientY: cyM }));
            mbItem.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window, button: 0, clientX: cxM, clientY: cyM }));
            mbItem.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window, clientX: cxM, clientY: cyM }));
            return;
        }

        // C. Handle Single-Tap File / Folder Open in DirListing
        var fileItem = target.closest('.jp-DirListing-item');
        if (fileItem) {
            var rectF = fileItem.getBoundingClientRect();
            var cxF = rectF.left + rectF.width / 2;
            var cyF = rectF.top + rectF.height / 2;
            fileItem.dispatchEvent(new MouseEvent('dblclick', {
                bubbles: true,
                cancelable: true,
                view: window,
                clientX: cxF,
                clientY: cyF,
                button: 0
            }));
            return;
        }

        // D. Handle Jupyter Native Toolbar Buttons on Touch
        var tbBtn = target.closest('.jp-ToolbarButtonComponent, jp-button, button[data-command]');
        if (tbBtn && !tbBtn.closest('#ml-floating-toolbar') && !tbBtn.closest('#ml-vertical-dock')) {
            var cmdTb = tbBtn.getAttribute('data-command');
            if (cmdTb) {
                e.preventDefault();
                runJupyterCmd(cmdTb);
            }
        }

        // E. Handle Single-Tap on Cell Editor -> Immediate Edit Mode & Blinking Cursor (No Text Selection or Lumino Context Menu)
        var cell = target.closest('.jp-Cell, .jp-CodeCell');
        var editorArea = target.closest('.cm-editor, .cm-content, .jp-InputArea, .jp-Cell-inputWrapper');
        var touchDuration = Date.now() - lastTouchTime;
        var touch = e.changedTouches && e.changedTouches[0];
        var dist = touch ? Math.hypot(touch.clientX - lastTouchX, touch.clientY - lastTouchY) : 0;

        if (cell && editorArea && touchDuration < 350 && dist < 12) {
            // Dismiss any open Lumino menus
            var openMenus = document.querySelectorAll('.lm-Menu');
            for (var m = 0; m < openMenus.length; m++) {
                var mw = getLuminoWidget(openMenus[m]);
                if (mw && typeof mw.close === 'function') {
                    try { mw.close(); } catch(err) {}
                } else {
                    openMenus[m].style.display = 'none';
                }
            }

            // Clear accidental text selection ranges
            var sel = window.getSelection && window.getSelection();
            if (sel && !sel.isCollapsed) {
                try { sel.removeAllRanges(); } catch(err) {}
            }

            // Activate cell
            document.querySelectorAll('.jp-Cell.jp-mod-active, .cell.selected').forEach(function(c) {
                if (c !== cell) c.classList.remove('jp-mod-active', 'selected');
            });
            cell.classList.add('jp-mod-active');

            // Switch Jupyter into edit mode and focus editor
            runJupyterCmd('notebook:enter-edit-mode');
            var cm = cell.querySelector('.cm-content');
            if (cm) {
                cm.focus();
            }
        }
    }, { capture: true });

    // Helper: Force Dark Theme on JupyterLab / Notebook 7
    function forceJupyterDarkTheme() {
        var app = window.jupyterapp || window.jupyterlab;
        if (app && app.commands) {
            try {
                app.commands.execute('apputils:change-theme', { theme: 'JupyterLab Dark' });
            } catch(e) {}
        }
        try {
            document.documentElement.setAttribute('data-jp-theme-light', 'false');
            document.documentElement.setAttribute('data-jp-theme-name', 'JupyterLab Dark');
            if (document.body) {
                document.body.setAttribute('data-jp-theme-light', 'false');
                document.body.classList.remove('jp-theme-light');
                document.body.classList.add('jp-theme-dark');
            }
        } catch(e) {}
    }

    // Inject mobile CSS tokens
    var style = document.createElement('style');
    style.id = 'mobilelinux-touch-styles';
    style.textContent = [
        '/* Mobile Touch Optimizations */',
        '.cm-editor, .cm-content, .jp-InputArea { -webkit-touch-callout: none !important; }',
        '.lm-Menu-item { min-height: 44px !important; padding: 10px 16px !important; font-size: 14px !important; touch-action: manipulation !important; -webkit-tap-highlight-color: rgba(243, 118, 38, 0.2) !important; cursor: pointer !important; }',
        '.lm-Menu-item:active { background: #f37626 !important; color: #fff !important; }',
        '.lm-MenuBar-item { min-height: 38px !important; padding: 8px 12px !important; font-size: 14px !important; touch-action: manipulation !important; cursor: pointer !important; }',

        '/* Per-Cell Run Button (Positioned Outside & Above Editor Border, Aligned with Prompt) */',
        '.jp-Cell, .jp-CodeCell, .cell.code_cell { position: relative !important; }',
        '.jp-InputArea, .jp-Cell-inputWrapper, .input_area { position: relative !important; padding-top: 4px !important; }',
        '.jp-InputPrompt { min-height: 28px !important; line-height: 28px !important; padding-top: 0 !important; padding-bottom: 0 !important; display: flex !important; align-items: center !important; }',
        '.jp-InputArea-editor { margin-top: 6px !important; }',
        '.ml-cell-play-btn { position: absolute !important; top: 4px !important; right: 8px !important; z-index: 30 !important; display: inline-flex !important; align-items: center !important; justify-content: center !important; width: 26px !important; height: 26px !important; min-width: 26px !important; min-height: 26px !important; border-radius: 6px !important; background: #238636 !important; border: 1px solid #2ea043 !important; color: #ffffff !important; cursor: pointer !important; box-shadow: 0 2px 5px rgba(0,0,0,0.35) !important; touch-action: manipulation !important; -webkit-user-select: none !important; user-select: none !important; transition: transform 0.1s ease, background 0.15s ease !important; }',
        '.ml-cell-play-btn:active { transform: scale(0.92) !important; background: #2ea043 !important; }',
        '.ml-cell-play-btn.ml-running { background: #d29922 !important; border-color: #bb8009 !important; }',
        '.ml-cell-play-btn.ml-success { background: #238636 !important; border-color: #2ea043 !important; }',
        '@keyframes ml-spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }',
        '.ml-spin { animation: ml-spin 0.8s linear infinite !important; }',

        '/* Minimal Vertical Mini-Dock for Tree View (Bottom-Right) */',
        '#ml-vertical-dock { position: fixed; right: 14px; bottom: 24px; z-index: 9999; display: flex; flex-direction: column; gap: 8px; align-items: center; pointer-events: auto; }',
        '.ml-vdock-btn { width: 38px; height: 38px; border-radius: 50%; border: 1px solid #30363d; background: #21262d; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3); cursor: pointer; touch-action: manipulation; -webkit-tap-highlight-color: transparent; transition: transform 0.15s ease, background 0.15s ease; }',
        '.ml-vdock-btn:hover { background: #2b323b; }',
        '.ml-vdock-btn:active { transform: scale(0.92); background: #30363d; }',
        '.ml-vdock-btn svg { stroke: #c9d1d9; }',
        '.ml-vdock-btn#ml-action-new-nb svg { stroke: #f0883e; }',

        '/* Floating Toolbar (Default Sleek Dark Theme matching MobileLinux) */',
        '#ml-floating-toolbar { position: fixed; bottom: 16px; right: 14px; z-index: 10000; display: flex; align-items: center; justify-content: flex-end; gap: 8px; pointer-events: none; transition: bottom 0.15s ease-out; }',
        '#ml-bar-inner { display: flex; align-items: center; gap: 6px; background: rgba(22, 27, 34, 0.96); backdrop-filter: blur(10px); padding: 6px 8px; border-radius: 8px; border: 1px solid #30363d; box-shadow: 0 4px 16px rgba(0,0,0,0.35); pointer-events: auto; overflow-x: auto; max-width: calc(100vw - 76px); }',
        '#ml-bar-inner.ml-collapsed { display: none; }',
        '.ml-bar-btn { background: #21262d; color: #c9d1d9; border: 1px solid #30363d; padding: 6px 11px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap; touch-action: manipulation; display: inline-flex; align-items: center; justify-content: center; transition: background 0.12s ease, border-color 0.12s ease; }',
        '.ml-bar-btn:active { background: #30363d; color: #f0f6fc; }',
        '.ml-btn-run { background: #238636 !important; color: #ffffff !important; font-weight: 600 !important; border-color: #2ea043 !important; }',
        '.ml-btn-run:active { background: #2ea043 !important; }',
        '.ml-btn-stop { background: #da3633 !important; color: #ffffff !important; border-color: #f85149 !important; }',
        '.ml-btn-stop:active { background: #b62324 !important; }',
        '.ml-btn-add { background: #21262d; color: #58a6ff; border-color: #30363d; }',
        '.ml-btn-toggle { background: #21262d; color: #58a6ff; border-color: #388bfd; }',
        '.ml-btn-collapse { background: #161b22; color: #8b949e; width: 36px; height: 36px; border-radius: 8px; display: flex; align-items: center; justify-content: center; border: 1px solid #30363d; box-shadow: 0 2px 8px rgba(0,0,0,0.3); pointer-events: auto; cursor: pointer; }',
        '.ml-btn-collapse:active { transform: scale(0.92); background: #21262d; color: #f0f6fc; }',

        '/* Keyboard Strip (Harmonious Dark Theme, Unified Buttons, No Bright Patches) */',
        '#ml-keys-strip { position: fixed; bottom: 65px; left: 0; right: 0; background: rgba(22, 27, 34, 0.96); backdrop-filter: blur(12px); padding: 6px 10px; display: none; gap: 6px; overflow-x: auto; z-index: 9999; border-top: 1px solid #30363d; box-shadow: 0 -3px 12px rgba(0,0,0,0.25); transition: bottom 0.15s ease-out; }',
        '#ml-keys-strip.ml-visible { display: flex; }',
        '.ml-key-btn { background: #21262d; color: #c9d1d9; border: 1px solid #30363d; border-radius: 6px; padding: 6px 10px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace; font-size: 13px; cursor: pointer; white-space: nowrap; touch-action: manipulation; display: inline-flex; align-items: center; justify-content: center; transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease; }',
        '.ml-key-btn:active { background: #30363d; color: #f0f6fc; }',
        '.ml-k-action { border-color: #388bfd !important; color: #58a6ff !important; font-weight: 600 !important; }',
        '.ml-k-action:active { background: #388bfd33 !important; }',
        '.ml-key-btn.ml-k-done { border-color: #3fb950 !important; color: #3fb950 !important; }',
        '.ml-k-code { color: #d2a8ff !important; font-weight: 500 !important; }',
        '.ml-k-esc { color: #ff7b72 !important; }',

        '/* Enforce Dark Theme on Jupyter */',
        ':root { color-scheme: dark !important; }',
        'html, body { background-color: #181c24 !important; color: #eceff1 !important; }',
        '#main, .jp-NotebookPanel, .jp-Notebook, #jp-main-content-panel { background: #181c24 !important; }'
    ].join('\n');
    document.head.appendChild(style);

    // Continuous poll and attach
    setInterval(function() {
        forceJupyterDarkTheme();
        attachPlayButtons();
        addMobileDashboardToolbar();
        injectFloatingToolbar();
        observeNotebookMutations();
    }, 1000);

    // Initial run
    forceJupyterDarkTheme();
    attachPlayButtons();
    addMobileDashboardToolbar();
    injectFloatingToolbar();
    observeNotebookMutations();
})();
