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

    // 5. Dashboard Action Toolbar on /tree or /
    function addMobileDashboardToolbar() {
        if (document.getElementById('mobilelinux-touch-bar')) return;
        if (!document.body) return;
        var p = window.location.pathname || '';
        if (p.indexOf('/tree') === -1 && p !== '/' && !p.endsWith('/')) return;

        var bar = document.createElement('div');
        bar.id = 'mobilelinux-touch-bar';
        bar.innerHTML = [
            '<button class="ml-dash-btn ml-btn-nb" id="ml-action-new-nb">＋ Notebook</button>',
            '<button class="ml-dash-btn ml-btn-folder" id="ml-action-new-folder">＋ Folder</button>',
            '<button class="ml-dash-btn ml-btn-lab" id="ml-action-open-lab">⚡ Open Lab</button>'
        ].join('');
        document.body.appendChild(bar);

        document.getElementById('ml-action-new-nb').addEventListener('click', function(ev) {
            ev.preventDefault(); ev.stopPropagation();
            if (!runJupyterCmd('notebook:create-new', { isLauncher: true })) {
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
    }

    // 6. Direct Touch Fix for Lumino Dropdown Menus, MenuBars, File Listing & Toolbars (CRITICAL)
    document.addEventListener('touchstart', function(e) {
        var menuItem = e.target.closest('.lm-Menu-item');
        if (menuItem) {
            var rect = menuItem.getBoundingClientRect();
            var cx = rect.left + rect.width / 2;
            var cy = rect.top + rect.height / 2;
            menuItem.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, button: 0 }));
            menuItem.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, button: 0 }));
        }
    }, { capture: true, passive: true });

    document.addEventListener('touchend', function(e) {
        var target = e.target;
        if (!target) return;

        // A. Handle Lumino Dropdown Menu Items
        var menuItem = target.closest('.lm-Menu-item');
        if (menuItem) {
            var hasSubmenu = menuItem.classList.contains('lm-mod-has-submenu') || menuItem.querySelector('.lm-Menu-itemSubmenuIcon') !== null;
            if (hasSubmenu) {
                return; // Let Lumino expand the submenu
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
                if (!runJupyterCmd('notebook:create-new', { isLauncher: true })) {
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

            var menu = menuItem.closest('.lm-Menu');
            if (menu) {
                menu.style.display = 'none';
                setTimeout(function() {
                    if (menu.parentNode) {
                        menu.parentNode.removeChild(menu);
                    }
                }, 100);
            }
            return;
        }

        // B. Handle Single-Tap File / Folder Open in DirListing
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

        // C. Handle Jupyter Native Toolbar Buttons on Touch
        var tbBtn = target.closest('.jp-ToolbarButtonComponent, jp-button, button[data-command]');
        if (tbBtn && !tbBtn.closest('#ml-floating-toolbar') && !tbBtn.closest('#mobilelinux-touch-bar')) {
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
        '.lm-Menu-item { min-height: 46px !important; padding: 12px 18px !important; font-size: 15px !important; touch-action: manipulation !important; -webkit-tap-highlight-color: rgba(59, 130, 246, 0.3) !important; cursor: pointer !important; }',
        '.lm-Menu-item:active { background: #3b82f6 !important; color: #fff !important; }',
        '.lm-MenuBar-item { min-height: 40px !important; padding: 10px 14px !important; font-size: 14px !important; touch-action: manipulation !important; }',
        '.ml-cell-play-btn { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 8px; background: #10b981; color: #fff; margin-right: 8px; cursor: pointer; box-shadow: 0 2px 5px rgba(0,0,0,0.2); touch-action: manipulation; -webkit-user-select: none; font-size: 14px; }',
        '.ml-cell-play-btn.ml-running { background: #f59e0b; animation: ml-pulse 1s infinite; }',
        '.ml-cell-play-btn.ml-success { background: #059669; }',
        '@keyframes ml-pulse { 0% { opacity: 1; } 50% { opacity: 0.6; } 100% { opacity: 1; } }',
        '#mobilelinux-touch-bar { position: fixed; bottom: 16px; left: 50%; transform: translateX(-50%); z-index: 10000; display: flex; gap: 10px; background: rgba(15, 23, 42, 0.92); backdrop-filter: blur(8px); padding: 8px 14px; border-radius: 28px; border: 1px solid rgba(255,255,255,0.15); box-shadow: 0 6px 20px rgba(0,0,0,0.4); }',
        '.ml-dash-btn { background: #3b82f6; color: #fff; border: none; padding: 8px 16px; border-radius: 20px; font-size: 14px; font-weight: 600; cursor: pointer; touch-action: manipulation; box-shadow: 0 2px 6px rgba(59,130,246,0.3); }',
        '.ml-dash-btn:active { opacity: 0.8; transform: scale(0.96); }',
        '.ml-btn-folder { background: #6366f1 !important; }',
        '.ml-btn-lab { background: #8b5cf6 !important; }',
        '#ml-floating-toolbar { position: fixed; bottom: 16px; left: 16px; right: 16px; z-index: 10000; display: flex; align-items: center; justify-content: space-between; pointer-events: none; }',
        '#ml-bar-inner { display: flex; gap: 6px; background: rgba(15, 23, 42, 0.92); backdrop-filter: blur(8px); padding: 6px 10px; border-radius: 24px; border: 1px solid rgba(255,255,255,0.15); box-shadow: 0 4px 16px rgba(0,0,0,0.4); pointer-events: auto; overflow-x: auto; max-width: calc(100% - 50px); }',
        '#ml-bar-inner.ml-collapsed { display: none; }',
        '.ml-bar-btn { background: #1e293b; color: #f1f5f9; border: 1px solid rgba(255,255,255,0.1); padding: 6px 12px; border-radius: 16px; font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap; touch-action: manipulation; }',
        '.ml-bar-btn:active { background: #334155; }',
        '.ml-btn-run { background: #10b981 !important; color: #fff !important; font-weight: 600 !important; }',
        '.ml-btn-collapse { background: #3b82f6; color: #fff; width: 38px; height: 38px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: none; box-shadow: 0 3px 10px rgba(0,0,0,0.3); pointer-events: auto; font-size: 16px; }',
        '#ml-keys-strip { position: fixed; bottom: 70px; left: 0; right: 0; background: rgba(15, 23, 42, 0.95); backdrop-filter: blur(10px); padding: 8px 12px; display: none; gap: 6px; overflow-x: auto; z-index: 9999; border-top: 1px solid rgba(255,255,255,0.1); }',
        '#ml-keys-strip.ml-visible { display: flex; }',
        '.ml-key-btn { background: #334155; color: #f8fafc; border: 1px solid rgba(255,255,255,0.1); border-radius: 8px; padding: 6px 10px; font-family: monospace; font-size: 13px; cursor: pointer; white-space: nowrap; touch-action: manipulation; }',
        '.ml-key-btn:active { background: #475569; }',
        '.ml-k-esc { background: #dc2626 !important; }'
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
