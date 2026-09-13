# 🪐 Jupyter Notebook for MobileLinux Code IDE

A full-featured interactive Jupyter Notebook extension for MobileLinux Code IDE, modeled after the Visual Studio Code Jupyter extension.

---

## 🚀 Features

1. **Interactive Code Cells (`# %%`)**
   - Write standard Python code divided into cells using `# %%`.
   - Tap **`▶ Run Cell`** or press `Ctrl+Enter` to execute only the active cell in Ubuntu PRoot Python.
   - Execution duration and exit codes are measured in real time.

2. **Real-Time Interactive Output Drawer**
   - Live stdout and stderr streaming directly inside the editor without context switching.
   - Shows formatted outputs with green stdout and red traceback indicators.

3. **Jupyter Server Integration**
   - Tap **`Server`** to start a local Jupyter Notebook / JupyterLab server (`http://localhost:8888`) in the terminal.
   - Test and interact directly through MobileLinux's built-in Developer Browser.

4. **Notebook (.ipynb) Converter & Exporter**
   - Open any `.ipynb` JSON file and tap **`📖 .ipynb`** to convert it into clean Python with `# %%` cell markers.
   - Export any Python file with `# %%` cells into standard Jupyter `.ipynb` v4 format with one tap.

5. **Quick Keyboard Shortcuts**
   - `Ctrl+Enter`: Run active cell.
   - `Ctrl+Shift+Enter`: Insert new code cell.

---

## 📦 File Structure

```text
jupyter-notebook/
├── manifest.json   # Package metadata (ID, version, permissions)
├── main.js         # Core extension logic and lifecycle
├── styles.css      # Floating toolbar and output dock styles
├── icon.png        # Official Jupyter icon (128x128)
└── README.md       # Extension documentation
```

---

## 🛠️ How to Install in MobileLinux

1. Open **Code IDE** in MobileLinux.
2. Tap the **3-dot menu** (`⋮`) in the top bar.
3. Select **`🧩 Extensions`**.
4. Tap **`📥 Import Extension (.mle / .zip)`**.
5. Select `jupyter-notebook.mle` or `jupyter-notebook.zip`.
6. Done! The extension is installed and ready to use immediately.
