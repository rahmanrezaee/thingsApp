
import os
import sys
import json
import logging
import time
import subprocess
import threading
import queue
import fnmatch
import shutil

# -------------------------------------------------------
#  GENCLAUDE SYNC SERVER V14.8 (With Interactive Selector)
# -------------------------------------------------------

NGROK_AUTH_TOKEN = "1XWWJL7UatlcC4ZpnfRT8LsrbEq_6QsrUjFRZpoVZNAp8mgwd"

try:
    from flask import Flask, request, jsonify, make_response, Response
    from flask_cors import CORS
except ImportError:
    print("\n[ERROR] Missing dependencies!")
    print("Please run: pip install flask flask-cors pyngrok watchdog\n")
    sys.exit(1)

try:
    from pyngrok import ngrok, conf
    HAS_NGROK = True
except ImportError:
    HAS_NGROK = False

try:
    from watchdog.observers import Observer
    from watchdog.events import FileSystemEventHandler
    HAS_WATCHDOG = True
except ImportError:
    print("\n[WARNING] 'watchdog' library not found. Live updates will not work.")
    HAS_WATCHDOG = False

app = Flask(__name__)
CORS(app)
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

if os.name == 'nt':
    import msvcrt
    os.system('')
else:
    import termios
    import tty

# --- Colors ---
C_CYAN = "\033[1;36m"
C_GREEN = "\033[32m"
C_YELLOW = "\033[33m"
C_RED = "\033[31m"
C_GREY = "\033[90m"
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_BLUE = "\033[34m"

# --- Config ---
CONFIG = {
    'ROOT_DIR': os.path.abspath(os.getcwd()),
    'MAX_FILE_SIZE': 5 * 1024 * 1024, # 5MB limit
    'EXCLUDED_PATHS': set(),
    'IGNORE_CONFIG_FILE': '.genclaude-ignore.json'
}

# --- DEFAULT IGNORE LIST ---
DEFAULT_IGNORE_DIRS = {
    'node_modules', '.git', '.vscode', '.idea', '__pycache__',
    'dist', 'build', 'coverage', '.next', '.nuxt', 'target', 'bin', 'obj', 'venv', 'env',
    '.gradle', 'out', 'generated', 'captures', 'android/app/build', 'ios/build', 'logs',
    '.dart_tool', '.genclaude', '.fvm', '.pub-cache', '.fetch-client', '.github'
}

BINARY_EXTENSIONS = {
    '.png', '.jpg', '.jpeg', '.gif', '.ico', '.svg', '.webp', '.bmp', '.tiff', '.psd', '.ai',
    '.ttf', '.otf', '.woff', '.woff2', '.eot',
    '.pdf', '.zip', '.tar', '.gz', '.7z', '.rar', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
    '.exe', '.dll', '.so', '.dylib', '.bin', '.obj', '.o', '.a', '.lib', '.pyc', '.pyd', '.class', '.jar', '.war', '.ear',
    '.apk', '.aab', '.ipa', '.ap_', '.dex', '.nib', '.plist',
    '.mp4', '.mp3', '.wav', '.ogg', '.mov', '.avi', '.webm', '.m4a',
    '.db', '.sqlite', '.sqlite3', '.parquet',
    '.DS_Store', 'Thumbs.db', '.lock', '.pem',
    '.log', '.map'
}

# Active ignore list (will be customized)
IGNORE_DIRS = set()
CUSTOM_IGNORE_PATTERNS = set()

# --- In-Memory File Index ---
FILE_INDEX = {}
INDEX_LOCK = threading.Lock()

# --- UI Helpers ---
def get_key():
    """Reads a single keypress with arrow support."""
    if os.name == 'nt':
        while True:
            if msvcrt.kbhit():
                key = msvcrt.getch()
                # Handle Special Keys (Arrows)
                if key in [b'\x00', b'\xe0']:
                    try:
                        key2 = msvcrt.getch()
                        if key2 == b'H': return 'up'
                        if key2 == b'P': return 'down'
                        if key2 == b'M': return 'right'
                        if key2 == b'K': return 'left'
                    except: pass
                    return None

                if key == b'\r': return 'enter'
                if key == b' ': return 'space'
                if key == b'\x08': return 'backspace'
                if key == b'\x1b': return 'esc'
                if key == b'\x03': sys.exit(0)

                try: return key.decode('utf-8').lower()
                except: return None
            time.sleep(0.01)
    else:
        fd = sys.stdin.fileno()
        old_settings = termios.tcgetattr(fd)
        try:
            tty.setraw(sys.stdin.fileno())
            ch = sys.stdin.read(1)
            if ch == '\x1b':
                next_ch = sys.stdin.read(1)
                if next_ch == '[':
                    final_ch = sys.stdin.read(1)
                    if final_ch == 'A': return 'up'
                    if final_ch == 'B': return 'down'
                    if final_ch == 'C': return 'right'
                    if final_ch == 'D': return 'left'
                return 'esc'
            if ch == '\r' or ch == '\n': return 'enter'
            if ch == ' ': return 'space'
            if ch == '\x7f': return 'backspace'
            if ch == '\x03': sys.exit(0)
            return ch.lower()
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)

def clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')

def save_ignore_config():
    """Save custom ignore list to file"""
    config_path = os.path.join(CONFIG['ROOT_DIR'], CONFIG['IGNORE_CONFIG_FILE'])
    config_data = {
        'ignore_dirs': list(IGNORE_DIRS),
        'custom_patterns': list(CUSTOM_IGNORE_PATTERNS),
        'excluded_paths': list(CONFIG['EXCLUDED_PATHS']),
        'timestamp': time.time()
    }
    try:
        with open(config_path, 'w') as f:
            json.dump(config_data, f, indent=2)
        print(f"{C_GREEN}✔ Saved ignore config to {CONFIG['IGNORE_CONFIG_FILE']}{C_RESET}")
    except Exception as e:
        print(f"{C_RED}✖ Failed to save config: {e}{C_RESET}")

def load_ignore_config():
    """Load custom ignore list from file"""
    config_path = os.path.join(CONFIG['ROOT_DIR'], CONFIG['IGNORE_CONFIG_FILE'])
    if os.path.exists(config_path):
        try:
            with open(config_path, 'r') as f:
                config_data = json.load(f)
            IGNORE_DIRS.update(config_data.get('ignore_dirs', []))
            CUSTOM_IGNORE_PATTERNS.update(config_data.get('custom_patterns', []))
            CONFIG['EXCLUDED_PATHS'].update(config_data.get('excluded_paths', []))
            return True
        except Exception as e:
            print(f"{C_YELLOW}⚠ Failed to load config: {e}{C_RESET}")
    return False

def run_interactive_selector():
    """Interactive file/folder selector with navigation"""
    # Load existing config or use defaults
    if load_ignore_config():
        print(f"{C_GREEN}✔ Loaded existing ignore configuration{C_RESET}")
        time.sleep(1)
    else:
        IGNORE_DIRS.update(DEFAULT_IGNORE_DIRS)

    # Auto-add default patterns if not present
    if '*~' not in CUSTOM_IGNORE_PATTERNS: CUSTOM_IGNORE_PATTERNS.add('*~')
    if '*.tmp' not in CUSTOM_IGNORE_PATTERNS: CUSTOM_IGNORE_PATTERNS.add('*.tmp')

    current_rel_path = "."
    cursor_idx = 0

    while True:
        full_current_path = os.path.abspath(os.path.join(CONFIG['ROOT_DIR'], current_rel_path))

        items = []
        try:
            all_items = [i for i in os.listdir(full_current_path) if not i.startswith('.')]
            dirs = sorted([i for i in all_items if os.path.isdir(os.path.join(full_current_path, i))], key=lambda s: s.lower())
            files = sorted([i for i in all_items if not os.path.isdir(os.path.join(full_current_path, i))], key=lambda s: s.lower())
            items = dirs + files
        except PermissionError:
            items = []

        clear_screen()
        print(f"{C_CYAN}="*70)
        print("  🚀 GENCLAUDE PROJECT NAVIGATOR".center(70))
        print("="*70 + f"{C_RESET}")

        print(f"\n  📁 Current: {C_BOLD}{current_rel_path}{C_RESET}")

        print(f"\n  {C_GREY}⌨️  Controls:{C_RESET}")
        print(f"  {C_GREY}├─ Navigation:  ↑↓/WS = Move   ←→/AD = In/Out   Enter = Open{C_RESET}")
        print(f"  {C_GREY}├─ Selection:   Space = Toggle   I = Invert All{C_RESET}")
        print(f"  {C_GREY}└─ Actions:     F = Finish      ESC = Quit{C_RESET}")

        # Calculate Stats
        excluded_count = 0
        for item in items:
            p = os.path.normpath(os.path.join(current_rel_path, item)).replace("\\", "/")
            if p.startswith("./"): p = p[2:]
            if p in CONFIG['EXCLUDED_PATHS'] or item in IGNORE_DIRS or item.lower() in IGNORE_DIRS:
                excluded_count += 1

        print(f"\n  📊 Stats: {len(items) - excluded_count} included, {excluded_count} excluded")
        print(f"{C_GREY}" + "-"*70 + f"{C_RESET}")

        if not items:
            print("  (Empty Directory)")

        # Scroll window logic
        MAX_ROWS = 15
        start_idx = max(0, cursor_idx - (MAX_ROWS // 2))
        end_idx = min(len(items), start_idx + MAX_ROWS)
        if end_idx - start_idx < MAX_ROWS:
            start_idx = max(0, end_idx - MAX_ROWS)

        for idx in range(start_idx, end_idx):
            item = items[idx]
            item_rel_path = os.path.normpath(os.path.join(current_rel_path, item)).replace("\\", "/")
            if item_rel_path.startswith("./"): item_rel_path = item_rel_path[2:]

            is_excluded = item_rel_path in CONFIG['EXCLUDED_PATHS'] or item in IGNORE_DIRS or item.lower() in IGNORE_DIRS
            full_item_path = os.path.join(full_current_path, item)
            is_dir = os.path.isdir(full_item_path)

            if idx == cursor_idx:
                cursor = "►"
                style = C_CYAN
            else:
                cursor = " "
                style = ""

            checkbox = "[ ]" if is_excluded else f"{C_GREEN}[✓]{C_RESET}"
            if is_excluded and idx != cursor_idx: style = C_GREY

            icon = "📁" if is_dir else "📄"

            print(f"  {style}{cursor} {checkbox} {icon}  {item}{C_RESET}")

        if len(items) > MAX_ROWS:
             print(f"  {C_GREY}... {len(items) - end_idx} more items ...{C_RESET}")

        print(f"{C_GREY}" + "-"*70 + f"{C_RESET}")
        print(f"  Total Exclusions: {len(CONFIG['EXCLUDED_PATHS'])}")
        print(f"{C_CYAN}="*70 + f"{C_RESET}")

        key = get_key()

        if key == 'f':
            # Save and exit
            save_ignore_config()
            break
        elif key == 'esc':
            print("\n  ❌ Cancelled by user")
            sys.exit(0)
        elif key == 'up' or key == 'w':
            cursor_idx = max(0, cursor_idx - 1)
        elif key == 'down' or key == 's':
            cursor_idx = min(len(items) - 1, cursor_idx + 1)
        elif key == 'space':
            if items:
                item = items[cursor_idx]
                item_rel_path = os.path.normpath(os.path.join(current_rel_path, item)).replace("\\", "/")
                if item_rel_path.startswith("./"): item_rel_path = item_rel_path[2:]

                if item_rel_path in CONFIG['EXCLUDED_PATHS']:
                    CONFIG['EXCLUDED_PATHS'].remove(item_rel_path)
                else:
                    CONFIG['EXCLUDED_PATHS'].add(item_rel_path)
        elif key == 'i':
            for item in items:
                item_rel_path = os.path.normpath(os.path.join(current_rel_path, item)).replace("\\", "/")
                if item_rel_path.startswith("./"): item_rel_path = item_rel_path[2:]
                if item_rel_path in CONFIG['EXCLUDED_PATHS']:
                    CONFIG['EXCLUDED_PATHS'].remove(item_rel_path)
                else:
                    CONFIG['EXCLUDED_PATHS'].add(item_rel_path)
        elif key == 'right' or key == 'enter' or key == 'd':
            if items:
                item = items[cursor_idx]
                full_item_path = os.path.join(full_current_path, item)
                if os.path.isdir(full_item_path):
                    current_rel_path = os.path.join(current_rel_path, item)
                    cursor_idx = 0
        elif key == 'left' or key == 'backspace' or key == 'a':
            if current_rel_path != ".":
                current_rel_path = os.path.dirname(current_rel_path)
                cursor_idx = 0

# --- Core Functions ---
def get_relative_path(path):
    try: return os.path.relpath(path, CONFIG['ROOT_DIR']).replace(os.sep, '/')
    except: return path

def is_path_excluded(rel_path):
    parts = rel_path.replace("\\", "/").split("/")
    for pattern in CUSTOM_IGNORE_PATTERNS:
        if pattern in rel_path or fnmatch.fnmatch(rel_path, pattern): return True
        if fnmatch.fnmatch(os.path.basename(rel_path), pattern): return True

    accum = ""
    for part in parts:
        accum = f"{accum}/{part}" if accum else part
        if accum in CONFIG['EXCLUDED_PATHS']: return True
        # Case insensitive check for ignore dirs (like node_modules vs Node_Modules)
        if part in IGNORE_DIRS or part.lower() in IGNORE_DIRS: return True
    return False

def is_valid_file(path, rel_path):
    if path.endswith('~'): return False
    if is_path_excluded(rel_path): return False
    ext = os.path.splitext(path)[1].lower()
    if ext in BINARY_EXTENSIONS: return False
    if os.path.basename(path).startswith('.'): return False
    return True

# --- Watchdog ---
event_queue = queue.Queue()

class ChangeHandler(FileSystemEventHandler):
    def on_any_event(self, event):
        if event.is_directory: return
        rel_path = get_relative_path(event.src_path)
        if not is_valid_file(event.src_path, rel_path): return
        event_type = event.event_type

        with INDEX_LOCK:
            if event_type == 'deleted':
                if rel_path in FILE_INDEX: del FILE_INDEX[rel_path]
            elif event_type == 'moved':
                if rel_path in FILE_INDEX: del FILE_INDEX[rel_path]
                if event.dest_path:
                    dest_rel = get_relative_path(event.dest_path)
                    if is_valid_file(event.dest_path, dest_rel):
                         FILE_INDEX[dest_rel] = {
                             "size": os.path.getsize(event.dest_path) if os.path.exists(event.dest_path) else 0,
                             "mtime": os.path.getmtime(event.dest_path) if os.path.exists(event.dest_path) else 0
                         }
            else:
                if os.path.exists(event.src_path):
                     FILE_INDEX[rel_path] = {
                         "size": os.path.getsize(event.src_path),
                         "mtime": os.path.getmtime(event.src_path)
                     }

        payload = {"type": event_type, "path": rel_path.replace("\\", "/"), "timestamp": time.time()}
        event_queue.put(payload)

def build_index():
    print(f"{C_GREY}[Index] Building file index...{C_RESET}")
    new_index = {}
    for root, dirs, filenames in os.walk(CONFIG['ROOT_DIR']):
        rel_dir = get_relative_path(root)
        if rel_dir == ".": rel_dir = ""

        # Filter directories in-place (case insensitive)
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and d.lower() not in IGNORE_DIRS and not d.startswith('.') and not is_path_excluded(os.path.join(rel_dir, d).replace("\\", "/"))]

        for name in filenames:
            path = os.path.join(root, name)
            rel_path = get_relative_path(path)
            if is_valid_file(path, rel_path):
                try:
                    stats = os.stat(path)
                    if stats.st_size <= CONFIG['MAX_FILE_SIZE']:
                        new_index[rel_path] = { "size": stats.st_size, "mtime": stats.st_mtime }
                except: pass
    with INDEX_LOCK:
        FILE_INDEX.clear()
        FILE_INDEX.update(new_index)
    print(f"{C_GREEN}[Index] Complete. Indexed {len(FILE_INDEX)} files.{C_RESET}")

def start_observer():
    if not HAS_WATCHDOG: return None
    observer = Observer()
    observer.schedule(ChangeHandler(), CONFIG['ROOT_DIR'], recursive=True)
    observer.start()
    return observer

# --- Routes ---
@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "root": os.path.basename(CONFIG['ROOT_DIR']), "capabilities": {"exec": True, "watch": HAS_WATCHDOG}, "indexed_files": len(FILE_INDEX)})

@app.route('/api/events')
def sse_events():
    def stream():
        while True:
            try:
                data = event_queue.get(timeout=5)
                yield f"data: {json.dumps(data)}\n\n"
            except: yield ": keep-alive\n\n"
    return Response(stream(), mimetype='text/event-stream')

@app.route('/api/list', methods=['GET'])
def list_files():
    with INDEX_LOCK:
        return jsonify({"files": [{"path": k, "size": v["size"], "mtime": v.get("mtime", 0)} for k, v in FILE_INDEX.items()]})

@app.route('/api/read', methods=['POST'])
def read_file():
    rel_path = request.json.get('path')
    if not rel_path: return jsonify({"error": "No path"}), 400
    full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
    try:
        if os.path.exists(full_path) and os.path.getsize(full_path) > CONFIG['MAX_FILE_SIZE']:
             return jsonify({"error": "File too large to read"}), 400

        with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
            return jsonify({"path": rel_path, "content": f.read()})
    except Exception as e: return jsonify({"error": str(e)}), 500

@app.route('/api/batch-read', methods=['POST'])
def batch_read():
    paths = request.json.get('paths', [])
    results = []
    for rel_path in paths:
        full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
        try:
            if os.path.exists(full_path) and os.path.getsize(full_path) <= CONFIG['MAX_FILE_SIZE']:
                with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
                    results.append({"path": rel_path, "content": f.read()})
        except: pass
    return jsonify({"files": results})

@app.route('/api/write', methods=['POST'])
def write_file():
    data = request.json
    rel_path = data.get('path')
    content = data.get('content')
    if not rel_path or content is None: return jsonify({"error": "Missing args"}), 400
    full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
    try:
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, 'w', encoding='utf-8', newline='\n') as f: f.write(content)
        try:
            stats = os.stat(full_path)
            with INDEX_LOCK: FILE_INDEX[rel_path] = { "size": stats.st_size, "mtime": stats.st_mtime }
        except: pass
        return jsonify({"status": "ok", "path": rel_path})
    except Exception as e: return jsonify({"error": str(e)}), 500

@app.route('/api/delete', methods=['POST'])
def delete_item():
    data = request.json
    rel_path = data.get('path')
    if not rel_path: return jsonify({"error": "No path"}), 400
    full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
    try:
        if os.path.isdir(full_path):
            shutil.rmtree(full_path)
        elif os.path.exists(full_path):
            os.remove(full_path)
        with INDEX_LOCK:
            if rel_path in FILE_INDEX: del FILE_INDEX[rel_path]
        return jsonify({"status": "ok"})
    except Exception as e: return jsonify({"error": str(e)}), 500

@app.route('/api/move', methods=['POST'])
def move_item():
    data = request.json
    src = data.get('source')
    dst = data.get('destination')
    if not src or not dst: return jsonify({"error": "Missing args"}), 400
    full_src = os.path.join(CONFIG['ROOT_DIR'], src)
    full_dst = os.path.join(CONFIG['ROOT_DIR'], dst)
    try:
        os.makedirs(os.path.dirname(full_dst), exist_ok=True)
        shutil.move(full_src, full_dst)
        return jsonify({"status": "ok"})
    except Exception as e: return jsonify({"error": str(e)}), 500

@app.route('/api/exec', methods=['POST'])
def exec_cmd():
    cmd = request.json.get('command')
    if not cmd: return jsonify({"error": "No command"}), 400
    try:
        result = subprocess.run(cmd, shell=True, cwd=CONFIG['ROOT_DIR'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=30)
        return jsonify({"stdout": result.stdout, "stderr": result.stderr, "code": result.returncode})
    except Exception as e: return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    run_interactive_selector()

    clear_screen()
    print(f"\n{C_CYAN}🚀 Starting GenClaude Server...{C_RESET}")
    build_index()
    observer = start_observer()
    print(f"\n{C_CYAN}💫 GenClaude Server V14.8 Running{C_RESET}")

    if HAS_NGROK and (NGROK_AUTH_TOKEN or os.environ.get("NGROK_AUTHTOKEN")):
        try:
            ngrok.set_auth_token(NGROK_AUTH_TOKEN or os.environ.get("NGROK_AUTHTOKEN"))
            print(f" {C_GREEN}✔ Ngrok: {C_BOLD}{ngrok.connect(8000).public_url}{C_RESET}")
        except: pass

    print(f" {C_GREEN}✔ Local: {C_BOLD}http://127.0.0.1:8000{C_RESET}")
    print(f"\n{C_CYAN}{'='*70}{C_RESET}")
    print(f" {C_GREEN}✨ Server is running! Press Ctrl+C to stop.{C_RESET}")
    print(f"{C_CYAN}{'='*70}{C_RESET}\n")

    try: app.run(port=8000, debug=False, threaded=True)
    finally:
        if observer: observer.stop(); observer.join()
