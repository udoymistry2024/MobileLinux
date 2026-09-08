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
            var inputWrapper = cell.querySelector('.jp-Cell-inputWrapper') || cell.querySelector('.input_area') || cell.querySelector('.jp-InputArea');

            if (!promptElem && !inputWrapper) continue;

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

            if (promptElem && promptElem.parentNode) {
                promptElem.parentNode.insertBefore(btn, promptElem);
            } else if (inputWrapper && inputWrapper.parentNode) {
                inputWrapper.parentNode.insertBefore(btn, inputWrapper);
            } else if (promptElem) {
                promptElem.appendChild(btn);
            }
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
    document.addEventListener('selectionchange', function() {
        var sel = window.getSelection && window.getSelection();
        if (sel && sel.toString()) {
            lastSelectedText = sel.toString();
        }
    });

    function copySelectedCode() {
        var text = '';
        var sel = window.getSelection && window.getSelection();
        if (sel && sel.toString()) {
            text = sel.toString();
        } else if (lastSelectedText) {
            text = lastSelectedText;
        }

        if (!text) {
            var cell = getActiveCell();
            var cm = cell && cell.querySelector('.cm-content');
            if (cm) {
                try { document.execCommand('copy'); } catch(e) {}
            }
        }

        if (text) {
            window.__ml_clipboard = text;
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
            var orig = btn.innerHTML;
            btn.innerHTML = '✓ Copied';
            btn.classList.add('ml-k-done');
            setTimeout(function() {
                btn.innerHTML = orig;
                btn.classList.remove('ml-k-done');
            }, 1000);
        }
    }

    function pasteCode() {
        var pasted = false;
        if (window.MobileLinuxClipboard && window.MobileLinuxClipboard.pasteText) {
            try {
                var sysText = window.MobileLinuxClipboard.pasteText();
                if (sysText) {
                    insertCodeText(sysText);
                    pasted = true;
                }
            } catch(e) {}
        }

        if (!pasted) {
            if (navigator.clipboard && navigator.clipboard.readText) {
                navigator.clipboard.readText().then(function(t) {
                    if (t) insertCodeText(t);
                    else if (window.__ml_clipboard) insertCodeText(window.__ml_clipboard);
                    else document.execCommand('paste');
                }).catch(function() {
                    if (window.__ml_clipboard) insertCodeText(window.__ml_clipboard);
                    else document.execCommand('paste');
                });
            } else if (window.__ml_clipboard) {
                insertCodeText(window.__ml_clipboard);
            } else {
                try { document.execCommand('paste'); } catch(e) {}
            }
        }

        var btn = document.getElementById('ml-k-paste');
        if (btn) {
            var orig = btn.innerHTML;
            btn.innerHTML = '✓ Pasted';
            btn.classList.add('ml-k-done');
            setTimeout(function() {
                btn.innerHTML = orig;
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

        // Create Keyboard Strip with Rich Keys
        var keyStrip = document.createElement('div');
        keyStrip.id = 'ml-keys-strip';
        keyStrip.innerHTML = [
            '<button class="ml-key-btn ml-k-action" id="ml-k-copy" title="Copy Selected Text">📋 Copy</button>',
            '<button class="ml-key-btn ml-k-action" id="ml-k-paste" title="Paste Code">📥 Paste</button>',
            '<button class="ml-key-btn ml-k-tool" id="ml-k-undo" title="Undo">↩</button>',
            '<button class="ml-key-btn ml-k-tool" id="ml-k-redo" title="Redo">↪</button>',
            '<button class="ml-key-btn ml-k-nav" id="ml-k-left" title="Move Cursor Left">◀</button>',
            '<button class="ml-key-btn ml-k-nav" id="ml-k-right" title="Move Cursor Right">▶</button>',
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

    // Inject mobile CSS tokens
    var style = document.createElement('style');
    style.id = 'mobilelinux-touch-styles';
    style.textContent = [
        '/* Mobile Touch Optimizations */',
        '.cm-editor, .cm-content, .jp-InputArea { -webkit-touch-callout: none !important; }',
        '.lm-Menu-item { min-height: 44px !important; padding: 10px 16px !important; font-size: 14px !important; touch-action: manipulation !important; -webkit-tap-highlight-color: rgba(243, 118, 38, 0.2) !important; cursor: pointer !important; }',
        '.lm-Menu-item:active { background: #f37626 !important; color: #fff !important; }',
        '.lm-MenuBar-item { min-height: 38px !important; padding: 8px 12px !important; font-size: 14px !important; touch-action: manipulation !important; cursor: pointer !important; }',
        '.ml-cell-play-btn { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; min-width: 28px; border-radius: 6px; background: #10b981; color: #fff; margin-right: 6px; margin-top: 4px; cursor: pointer; box-shadow: 0 1px 4px rgba(0,0,0,0.15); touch-action: manipulation; -webkit-user-select: none; user-select: none; font-size: 13px; flex-shrink: 0; z-index: 10; }',
        '.ml-cell-play-btn:active { transform: scale(0.92); }',
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
        '    #ml-bar-inner { background: rgba(30, 30, 30, 0.98) !important; border-color: #444 !important; box-shadow: 0 4px 16px rgba(0,0,0,0.4) !important; }',
        '    .ml-bar-btn { background: #2d2d2d !important; color: #e5e7eb !important; border-color: #444 !important; }',
        '    .ml-bar-btn:active { background: #3d3d3d !important; }',
        '    .ml-btn-collapse { background: #252526 !important; border-color: #444 !important; color: #f37626 !important; }',
        '    #ml-keys-strip { background: rgba(30, 30, 30, 0.98) !important; border-top-color: #444 !important; }',
        '    .ml-key-btn { background: #2d2d2d !important; color: #e5e7eb !important; border-color: #444 !important; }',
        '    .ml-k-action { background: #0c4a6e !important; color: #7dd3fc !important; border-color: #0284c7 !important; }',
        '    .ml-k-nav { background: #1e293b !important; color: #cbd5e1 !important; border-color: #334155 !important; }',
        '    .ml-k-code { background: #3b0764 !important; color: #f0abfc !important; border-color: #701a75 !important; }',
        '}',
        '[data-jp-theme-light="false"] .ml-vdock-btn, .jp-theme-dark .ml-vdock-btn { background: #252526 !important; border-color: #3c3c3c !important; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4) !important; }',
        '[data-jp-theme-light="false"] .ml-vdock-btn svg, .jp-theme-dark .ml-vdock-btn svg { stroke: #d1d5db !important; }',
        '[data-jp-theme-light="false"] .ml-vdock-btn#ml-action-new-nb svg, .jp-theme-dark .ml-vdock-btn#ml-action-new-nb svg { stroke: #f37626 !important; }',
        '[data-jp-theme-light="false"] #ml-bar-inner, .jp-theme-dark #ml-bar-inner { background: rgba(30, 30, 30, 0.98) !important; border-color: #444 !important; }',
        '[data-jp-theme-light="false"] .ml-bar-btn, .jp-theme-dark .ml-bar-btn { background: #2d2d2d !important; color: #e5e7eb !important; border-color: #444 !important; }',
        '[data-jp-theme-light="false"] .ml-btn-collapse, .jp-theme-dark .ml-btn-collapse { background: #252526 !important; border-color: #444 !important; }',
        '[data-jp-theme-light="false"] #ml-keys-strip, .jp-theme-dark #ml-keys-strip { background: rgba(30, 30, 30, 0.98) !important; border-top-color: #444 !important; }',
        '[data-jp-theme-light="false"] .ml-key-btn, .jp-theme-dark .ml-key-btn { background: #2d2d2d !important; color: #e5e7eb !important; border-color: #444 !important; }',
        '[data-jp-theme-light="false"] .ml-k-action, .jp-theme-dark .ml-k-action { background: #0c4a6e !important; color: #7dd3fc !important; border-color: #0284c7 !important; }',
        '[data-jp-theme-light="false"] .ml-k-nav, .jp-theme-dark .ml-k-nav { background: #1e293b !important; color: #cbd5e1 !important; border-color: #334155 !important; }',
        '[data-jp-theme-light="false"] .ml-k-code, .jp-theme-dark .ml-k-code { background: #3b0764 !important; color: #f0abfc !important; border-color: #701a75 !important; }',

        '/* Sleek Jupyter-Themed Floating Bar for Notebook View */',
        '#ml-floating-toolbar { position: fixed; bottom: 16px; right: 14px; z-index: 10000; display: flex; align-items: center; justify-content: flex-end; gap: 8px; pointer-events: none; transition: bottom 0.15s ease-out; }',
        '#ml-bar-inner { display: flex; align-items: center; gap: 6px; background: rgba(255, 255, 255, 0.98); backdrop-filter: blur(8px); padding: 6px 8px; border-radius: 8px; border: 1px solid #d0d7de; box-shadow: 0 4px 14px rgba(0,0,0,0.12); pointer-events: auto; overflow-x: auto; max-width: calc(100vw - 76px); }',
        '#ml-bar-inner.ml-collapsed { display: none; }',
        '.ml-bar-btn { background: #f6f8fa; color: #24292f; border: 1px solid #d0d7de; padding: 6px 11px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap; touch-action: manipulation; transition: background 0.1s ease; }',
        '.ml-bar-btn:active { background: #e5e7eb; }',
        '.ml-btn-run { background: #10b981 !important; color: #fff !important; font-weight: 600 !important; border-color: #059669 !important; }',
        '.ml-btn-collapse { background: #ffffff; color: #f37626; width: 36px; height: 36px; border-radius: 8px; display: flex; align-items: center; justify-content: center; border: 1px solid #d0d7de; box-shadow: 0 2px 8px rgba(0,0,0,0.12); pointer-events: auto; font-size: 15px; cursor: pointer; }',
        '.ml-btn-collapse:active { transform: scale(0.92); }',
        '#ml-keys-strip { position: fixed; bottom: 65px; left: 0; right: 0; background: rgba(255, 255, 255, 0.98); backdrop-filter: blur(10px); padding: 6px 10px; display: none; gap: 6px; overflow-x: auto; z-index: 9999; border-top: 1px solid #d0d7de; box-shadow: 0 -2px 8px rgba(0,0,0,0.08); transition: bottom 0.15s ease-out; }',
        '#ml-keys-strip.ml-visible { display: flex; }',
        '.ml-key-btn { background: #f6f8fa; color: #1f2937; border: 1px solid #d0d7de; border-radius: 6px; padding: 5px 9px; font-family: monospace; font-size: 13px; cursor: pointer; white-space: nowrap; touch-action: manipulation; }',
        '.ml-key-btn:active { background: #e5e7eb; }',
        '.ml-k-action { background: #e0f2fe !important; color: #0369a1 !important; border-color: #7dd3fc !important; font-weight: 600 !important; }',
        '.ml-k-action:active { background: #bae6fd !important; }',
        '.ml-k-done { background: #10b981 !important; color: #ffffff !important; border-color: #059669 !important; transition: background 0.15s ease; }',
        '.ml-k-nav { background: #f1f5f9 !important; font-weight: 700 !important; color: #334155 !important; }',
        '.ml-k-tool { font-weight: 600 !important; color: #4b5563 !important; }',
        '.ml-k-code { background: #fdf4ff !important; color: #a21caf !important; border-color: #f0abfc !important; font-weight: 500 !important; }',
        '.ml-k-esc { background: #fee2e2 !important; color: #dc2626 !important; border-color: #fca5a5 !important; }'
    ].join('\n');
    document.head.appendChild(style);

    // Continuous poll and attach
    setInterval(function() {
        attachPlayButtons();
        addMobileDashboardToolbar();
        injectFloatingToolbar();
        observeNotebookMutations();
    }, 1000);

    // Initial run
    attachPlayButtons();
    addMobileDashboardToolbar();
    injectFloatingToolbar();
    observeNotebookMutations();
})();
