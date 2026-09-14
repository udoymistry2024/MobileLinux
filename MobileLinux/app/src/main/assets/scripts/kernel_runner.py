#!/usr/bin/env python3
"""
MobileLinux Interactive Kernel Runner Daemon
Provides persistent, stateful code execution for Code IDE extensions (e.g. Jupyter Notebook).
Reads JSON lines from stdin, executes in persistent namespace, captures stdout/stderr, HTML representations and plots.
"""

import sys
import io
import os
import json
import base64
import traceback
import ast
import warnings

user_ns = {
    '__name__': '__main__',
    '__doc__': None,
    '__package__': None
}

class BlockedStdin(io.StringIO):
    """Prevents user code from hanging the kernel runner daemon if input() is invoked."""
    def read(self, *args, **kwargs):
        raise RuntimeError("Interactive input() is not supported in notebook cells.")
    def readline(self, *args, **kwargs):
        raise RuntimeError("Interactive input() is not supported in notebook cells.")

def capture_matplotlib_plot():
    try:
        if 'matplotlib' in sys.modules:
            import matplotlib.pyplot as plt
            if plt.get_fignums():
                buf = io.BytesIO()
                plt.savefig(buf, format='png', bbox_inches='tight', dpi=120)
                plt.close('all')
                return base64.b64encode(buf.getvalue()).decode('utf-8')
    except Exception:
        pass
    return None

def execute_code(code_str):
    stdout_capture = io.StringIO()
    stderr_capture = io.StringIO()
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    old_stdin = sys.stdin
    sys.stdout = stdout_capture
    sys.stderr = stderr_capture
    sys.stdin = BlockedStdin()
    error_text = None
    captured_html = None
    
    try:
        if 'matplotlib' in sys.modules:
            try:
                import matplotlib
                matplotlib.use('Agg')
                warnings.filterwarnings('ignore', category=UserWarning, module='matplotlib')
            except Exception:
                pass

        # Configure pandas display formatting to avoid wrapping on wide outputs
        if 'pandas' in sys.modules:
            try:
                import pandas as _pd
                _pd.set_option('display.width', 1000)
                _pd.set_option('display.max_columns', 50)
            except Exception:
                pass

        tree = ast.parse(code_str, filename='<notebook-cell>', mode='exec')
        if tree.body and isinstance(tree.body[-1], ast.Expr):
            last_expr = tree.body.pop()
            if tree.body:
                exec(compile(tree, '<notebook-cell>', 'exec'), user_ns)
            eval_res = eval(compile(ast.Expression(last_expr.value), '<notebook-cell>', 'eval'), user_ns)
            if eval_res is not None:
                html_repr = None
                if hasattr(eval_res, '_repr_html_') and callable(getattr(eval_res, '_repr_html_')):
                    try:
                        html_repr = eval_res._repr_html_()
                    except Exception:
                        html_repr = None
                elif hasattr(eval_res, 'to_html') and callable(getattr(eval_res, 'to_html')):
                    try:
                        html_repr = eval_res.to_html()
                    except Exception:
                        html_repr = None

                if html_repr and isinstance(html_repr, str):
                    captured_html = html_repr
                else:
                    print(repr(eval_res))
        else:
            exec(compile(tree, '<notebook-cell>', 'exec'), user_ns)
    except Exception:
        error_text = traceback.format_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        sys.stdin = old_stdin

    plot_b64 = capture_matplotlib_plot()

    res = {
        'status': 'error' if error_text else 'ok',
        'stdout': stdout_capture.getvalue(),
        'stderr': stderr_capture.getvalue(),
        'error': error_text,
        'image': plot_b64
    }
    if captured_html:
        res['html'] = captured_html
    return res

def main():
    ready_msg = json.dumps({'event': 'ready', 'pid': os.getpid()})
    sys.stdout.write(ready_msg + '\n')
    sys.stdout.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
            action = req.get('action', 'execute')
            req_id = req.get('id', '')

            if action == 'execute':
                code = req.get('code', '')
                res = execute_code(code)
                res['id'] = req_id
                res['event'] = 'result'
                sys.stdout.write(json.dumps(res) + '\n')
                sys.stdout.flush()
            elif action == 'restart':
                user_ns.clear()
                user_ns.update({'__name__': '__main__', '__doc__': None, '__package__': None})
                sys.stdout.write(json.dumps({'id': req_id, 'event': 'restarted', 'status': 'ok'}) + '\n')
                sys.stdout.flush()
            elif action == 'ping':
                sys.stdout.write(json.dumps({'id': req_id, 'event': 'pong'}) + '\n')
                sys.stdout.flush()
            elif action == 'exit':
                break
        except Exception as e:
            sys.stdout.write(json.dumps({'error': str(e), 'status': 'fatal'}) + '\n')
            sys.stdout.flush()

if __name__ == '__main__':
    main()
