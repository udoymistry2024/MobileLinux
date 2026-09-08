/* MobileLinux Ultimate Mobile UX for Jupyter Notebook 7 & JupyterLab */
(function() {
    if (window.__ml_touch_init) return;
    window.__ml_touch_init = true;

    // 1. Polyfill window.open to bypass mobile popup blockers and navigate in-tab or via WebChromeClient
    var origOpen = window.open;
    window.open = function(url, target, features) {
        if (!url) {
            var fakeWin = {
                opener: null,
                location: {
                    set href(val) { if (val) window.location.href = val; },
                    get href() { return window.location.href; }
                },
                focus: function() {},
                close: function() {}
            };
            try {
                var w = origOpen ? origOpen.call(window, '', target, features) : null;
                if (w) return w;
            } catch(e) {}
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
            var editor = cellElem.querySelector('.cm-content') ||
                         cellElem.querySelector('textarea') ||
                         cellElem.querySelector('.cm-editor') ||
                         cellElem;
            try {
                editor.focus();
                editor.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                editor.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                editor.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
            } catch(e) {}

            document.querySelectorAll('.jp-Cell.jp-mod-active, .cell.selected').forEach(function(c) {
                if (c !== cellElem) c.classList.remove('jp-mod-active', 'selected');
            });
            cellElem.classList.add('jp-mod-active');
        }

        if (btnElem) {
            btnElem.classList.add('ml-running');
            btnElem.classList.remove('ml-success');
            var icon = btnElem.querySelector('.ml-play-icon');
            if (icon) icon.textContent = '⟳';
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
                    var icon = btnElem.querySelector('.ml-play-icon');
                    if (icon) icon.textContent = '✓';
                    setTimeout(function() {
                        btnElem.classList.remove('ml-success');
                        if (icon) icon.textContent = '▶';
                    }, 1200);
                }
            }, 300);
        }
    }

    // Insert text at cursor in active CodeMirror editor (for virtual keys)
    function insertCodeText(text, offsetBack) {
        var cell = getActiveCell();
        var editor = (cell && cell.querySelector('.cm-content')) || document.activeElement;
        if (!editor) return;
        try {
            editor.focus();
            if (document.execCommand) {
                document.execCommand('insertText', false, text);
            } else {
                editor.dispatchEvent(new InputEvent('beforeinput', {
                    bubbles: true,
                    cancelable: true,
                    inputType: 'insertText',
                    data: text
                }));
            }
            if (offsetBack && window.getSelection) {
                var sel = window.getSelection();
                if (sel && sel.rangeCount > 0) {
                    var range = sel.getRangeAt(0);
                    range.setStart(range.startContainer, Math.max(0, range.startOffset - offsetBack));
                    range.collapse(true);
                    sel.removeAllRanges();
                    sel.addRange(range);
                }
            }
        } catch(e) {
            console.warn('[MobileLinux] Insert text failed:', e);
        }
    }

    // 2. Colab-Style Per-Cell Play Button Injection
    function attachPlayButtons() {
        var cells = document.querySelectorAll('.jp-Cell, .jp-CodeCell, .cell.code_cell');
        for (var i = 0; i < cells.length; i++) {
            var cell = cells[i];
            if (cell.dataset.mlPlayAttached === 'true') continue;

            var promptElem = cell.querySelector('.jp-InputPrompt') || cell.querySelector('.prompt.input_prompt');
            var inputWrapper = cell.querySelector('.jp-Cell-inputWrapper') || cell.querySelector('.input_area');

            if (!promptElem && !inputWrapper) continue;

            cell.dataset.mlPlayAttached = 'true';

            var btn = document.createElement('div');
            btn.className = 'ml-cell-play-btn';
            btn.title = 'Run cell';
            btn.innerHTML = '<span class="ml-play-icon">▶</span>';

            (function(c, b) {
                function onRun(ev) {
                    ev.preventDefault();
                    ev.stopPropagation();
                    executeCell(c, b);
                }
                b.addEventListener('click', onRun);
                b.addEventListener('touchend', onRun);
            })(cell, btn);

            if (promptElem) {
                promptElem.insertBefore(btn, promptElem.firstChild);
            } else if (inputWrapper) {
                inputWrapper.insertBefore(btn, inputWrapper.firstChild);
            }
        }
    }

    // 3. Floating Mobile Action Dock (Run, Add, Stop, Restart, Keys)
    function injectFloatingToolbar() {
        if (document.getElementById('ml-floating-toolbar')) return;
        if (!document.body) return;

        var p = window.location.pathname || '';
        var isNotebookPage = p.indexOf('/notebooks/') !== -1 || p.indexOf('/lab') !== -1 || document.querySelector('.jp-Notebook') !== null;
        if (!isNotebookPage) return;

        // Create Keyboard Strip
        var keyStrip = document.createElement('div');
        keyStrip.id = 'ml-keys-strip';
        keyStrip.innerHTML = [
            '<button class="ml-key-btn" id="ml-k-tab" title="Indent 4 spaces">Tab</button>',
            '<button class="ml-key-btn" id="ml-k-colon">:</button>',
            '<button class="ml-key-btn" id="ml-k-paren">( )</button>',
            '<button class="ml-key-btn" id="ml-k-bracket">[ ]</button>',
            '<button class="ml-key-btn" id="ml-k-brace">{ }</button>',
            '<button class="ml-key-btn" id="ml-k-quote">"</button>',
            '<button class="ml-key-btn" id="ml-k-squote">\'</button>',
            '<button class="ml-key-btn" id="ml-k-equal">=</button>',
            '<button class="ml-key-btn" id="ml-k-under">_</button>',
            '<button class="ml-key-btn" id="ml-k-hash">#</button>',
            '<button class="ml-key-btn" id="ml-k-def">def </button>',
            '<button class="ml-key-btn" id="ml-k-print">print()</button>',
            '<button class="ml-key-btn ml-k-esc" id="ml-k-esc">Esc</button>'
        ].join('');
        document.body.appendChild(keyStrip);

        // Bind virtual key actions
        document.getElementById('ml-k-tab').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('    '); });
        document.getElementById('ml-k-colon').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(':'); });
        document.getElementById('ml-k-paren').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('()', 1); });
        document.getElementById('ml-k-bracket').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('[]', 1); });
        document.getElementById('ml-k-brace').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('{}', 1); });
        document.getElementById('ml-k-quote').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('""', 1); });
        document.getElementById('ml-k-squote').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('\'\'', 1); });
        document.getElementById('ml-k-equal').addEventListener('click', function(e) { e.preventDefault(); insertCodeText(' = '); });
        document.getElementById('ml-k-under').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('_'); });
        document.getElementById('ml-k-hash').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('# '); });
        document.getElementById('ml-k-def').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('def '); });
        document.getElementById('ml-k-print').addEventListener('click', function(e) { e.preventDefault(); insertCodeText('print()', 1); });
        document.getElementById('ml-k-esc').addEventListener('click', function(e) {
            e.preventDefault();
            var target = document.activeElement || document;
            target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true }));
        });

        // Create Toolbar
        var bar = document.createElement('div');
        bar.id = 'ml-floating-toolbar';
        bar.innerHTML = [
            '<div id="ml-bar-inner">',
            '  <button class="ml-bar-btn ml-btn-run" id="ml-action-run">▶ Run</button>',
            '  <button class="ml-bar-btn" id="ml-action-next">▶+ Next</button>',
            '  <button class="ml-bar-btn ml-btn-add" id="ml-action-add-code">＋ Code</button>',
            '  <button class="ml-bar-btn ml-btn-add" id="ml-action-add-text">＋ Text</button>',
            '  <button class="ml-bar-btn ml-btn-stop" id="ml-action-stop">⏹ Stop</button>',
            '  <button class="ml-bar-btn" id="ml-action-restart">⟳ Restart</button>',
            '  <button class="ml-bar-btn ml-btn-toggle" id="ml-action-keys">⌨ Keys</button>',
            '</div>',
            '<button class="ml-bar-btn ml-btn-collapse" id="ml-action-collapse" title="Collapse / Expand Toolbar">⚡</button>'
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
            if (inner) {
                inner.classList.toggle('ml-collapsed');
            }
        });
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
    document.addEventListener('touchstart', function(e) {
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
    }, { capture: true });

    // Inject mobile CSS tokens
    var style = document.createElement('style');
    style.id = 'mobilelinux-touch-styles';
    style.textContent = [
        '/* Mobile Touch Optimizations */',
        '.lm-Menu-item { min-height: 44px !important; padding: 10px 16px !important; font-size: 14px !important; touch-action: manipulation !important; -webkit-tap-highlight-color: rgba(243, 118, 38, 0.2) !important; cursor: pointer !important; }',
        '.lm-Menu-item:active { background: #f37626 !important; color: #fff !important; }',
        '.lm-MenuBar-item { min-height: 38px !important; padding: 8px 12px !important; font-size: 14px !important; touch-action: manipulation !important; cursor: pointer !important; }',
        '.ml-cell-play-btn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 6px; background: #10b981; color: #fff; margin-right: 8px; cursor: pointer; box-shadow: 0 1px 4px rgba(0,0,0,0.15); touch-action: manipulation; -webkit-user-select: none; font-size: 13px; }',
        '.ml-cell-play-btn.ml-running { background: #f59e0b; animation: ml-pulse 1s infinite; }',
        '.ml-cell-play-btn.ml-success { background: #059669; }',
        '@keyframes ml-pulse { 0% { opacity: 1; } 50% { opacity: 0.6; } 100% { opacity: 1; } }',

        '/* Minimal Vertical Mini-Dock for Tree View (Bottom-Right, Jupyter Original Theme) */',
        '#ml-vertical-dock { position: fixed; right: 14px; bottom: 24px; z-index: 9999; display: flex; flex-direction: column; gap: 8px; align-items: center; pointer-events: auto; }',
        '.ml-vdock-btn { width: 38px; height: 38px; border-radius: 50%; border: 1px solid #d0d7de; background: #ffffff; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12); cursor: pointer; touch-action: manipulation; -webkit-tap-highlight-color: transparent; transition: transform 0.15s ease, background 0.15s ease; }',
        '.ml-vdock-btn:hover { background: #f6f8fa; }',
        '.ml-vdock-btn:active { transform: scale(0.92); background: #eaeef2; }',

        '/* Jupyter Dark Theme Compatibility */',
        '@media (prefers-color-scheme: dark) {',
        '    .ml-vdock-btn { background: #252526 !important; border-color: #3c3c3c !important; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4) !important; }',
        '    .ml-vdock-btn:hover { background: #333333 !important; }',
        '    .ml-vdock-btn:active { background: #3c3c3c !important; }',
        '    .ml-vdock-btn svg { stroke: #d1d5db !important; }',
        '    .ml-vdock-btn#ml-action-new-nb svg { stroke: #f37626 !important; }',
        '}',
        '[data-jp-theme-light="false"] .ml-vdock-btn, .jp-theme-dark .ml-vdock-btn { background: #252526 !important; border-color: #3c3c3c !important; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4) !important; }',
        '[data-jp-theme-light="false"] .ml-vdock-btn svg, .jp-theme-dark .ml-vdock-btn svg { stroke: #d1d5db !important; }',
        '[data-jp-theme-light="false"] .ml-vdock-btn#ml-action-new-nb svg, .jp-theme-dark .ml-vdock-btn#ml-action-new-nb svg { stroke: #f37626 !important; }',

        '/* Sleek Jupyter-Themed Floating Bar for Notebook View */',
        '#ml-floating-toolbar { position: fixed; bottom: 16px; right: 14px; z-index: 10000; display: flex; align-items: center; justify-content: flex-end; gap: 8px; pointer-events: none; }',
        '#ml-bar-inner { display: flex; gap: 6px; background: rgba(255, 255, 255, 0.96); backdrop-filter: blur(8px); padding: 5px 8px; border-radius: 20px; border: 1px solid #d0d7de; box-shadow: 0 4px 14px rgba(0,0,0,0.12); pointer-events: auto; overflow-x: auto; max-width: calc(100vw - 80px); }',
        '#ml-bar-inner.ml-collapsed { display: none; }',
        '.ml-bar-btn { background: #f6f8fa; color: #24292f; border: 1px solid #d0d7de; padding: 6px 11px; border-radius: 14px; font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap; touch-action: manipulation; }',
        '.ml-bar-btn:active { background: #e5e7eb; }',
        '.ml-btn-run { background: #10b981 !important; color: #fff !important; font-weight: 600 !important; border-color: #059669 !important; }',
        '.ml-btn-collapse { background: #ffffff; color: #f37626; width: 38px; height: 38px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 1px solid #d0d7de; box-shadow: 0 2px 8px rgba(0,0,0,0.12); pointer-events: auto; font-size: 16px; cursor: pointer; }',
        '.ml-btn-collapse:active { transform: scale(0.92); }',
        '#ml-keys-strip { position: fixed; bottom: 65px; left: 0; right: 0; background: rgba(255, 255, 255, 0.98); backdrop-filter: blur(10px); padding: 6px 10px; display: none; gap: 6px; overflow-x: auto; z-index: 9999; border-top: 1px solid #d0d7de; box-shadow: 0 -2px 8px rgba(0,0,0,0.08); }',
        '#ml-keys-strip.ml-visible { display: flex; }',
        '.ml-key-btn { background: #f6f8fa; color: #1f2937; border: 1px solid #d0d7de; border-radius: 6px; padding: 5px 9px; font-family: monospace; font-size: 13px; cursor: pointer; white-space: nowrap; touch-action: manipulation; }',
        '.ml-key-btn:active { background: #e5e7eb; }',
        '.ml-k-esc { background: #fee2e2 !important; color: #dc2626 !important; border-color: #fca5a5 !important; }'
    ].join('\n');
    document.head.appendChild(style);

    // Continuous poll and attach
    setInterval(function() {
        attachPlayButtons();
        addMobileDashboardToolbar();
        injectFloatingToolbar();
    }, 1000);

    // Initial run
    attachPlayButtons();
    addMobileDashboardToolbar();
    injectFloatingToolbar();
})();
