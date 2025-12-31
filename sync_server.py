import os
import sys
import json
import logging
import time
import subprocess
import threading

# -------------------------------------------------------
#  GENCLAUDE SYNC SERVER V11.3 (With Token Setup Guide)
# -------------------------------------------------------

# Your Ngrok Authtoken - Get it from: https://dashboard.ngrok.com/get-started/your-authtoken
NGROK_AUTH_TOKEN = "37aPnl08Z9xwh7SdWoEqwdpeClD_3Bb4zgFKSMNZMUY78jr1q"

try:
    from flask import Flask, request, jsonify, make_response
    from flask_cors import CORS
except ImportError:
    print("\n[ERROR] Missing dependencies!")
    print("Please run: pip install flask flask-cors pyngrok\n")
    sys.exit(1)

# Try importing pyngrok, but don't crash if missing (optional)
try:
    from pyngrok import ngrok, conf
    HAS_NGROK = True
except ImportError:
    HAS_NGROK = False

app = Flask(__name__)
CORS(app) # Enable CORS for all routes
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

if os.name == 'nt':
    import msvcrt
    os.system('') # Enable VT100 emulation on Windows
else:
    import termios
    import tty

# --- ANSI Colors ---
C_CYAN = "\033[1;36m"
C_GREEN = "\033[32m"
C_YELLOW = "\033[33m"
C_RED = "\033[31m"
C_GREY = "\033[90m"
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_BLUE = "\033[34m"

# --- Configuration ---
CONFIG = {
    'ROOT_DIR': os.path.abspath(os.getcwd()),
    'MAX_FILE_SIZE': 2 * 1024 * 1024, # 2MB limit for text files
    'EXCLUDED_PATHS': set()
}

# --- Filters ---
IGNORE_DIRS = {
    'node_modules', '.git', '.vscode', '.idea', '__pycache__',
    'dist', 'build', 'coverage', '.next', '.nuxt', 'target', 'bin', 'obj', 'venv', 'env',
    '.gradle', 'out', 'generated', 'captures', 'android/app/build', 'ios/build'
}

# Strict Extension Allowlist/Blocklist approach
BINARY_EXTENSIONS = {
    # Images
    '.png', '.jpg', '.jpeg', '.gif', '.ico', '.svg', '.webp', '.bmp', '.tiff', '.psd', '.ai',
    # Fonts
    '.ttf', '.otf', '.woff', '.woff2', '.eot',
    # Documents/Archives
    '.pdf', '.zip', '.tar', '.gz', '.7z', '.rar', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
    # Executables/Libs
    '.exe', '.dll', '.so', '.dylib', '.bin', '.obj', '.o', '.a', '.lib', '.pyc', '.pyd', '.class', '.jar', '.war', '.ear',
    # Mobile/Platform
    '.apk', '.aab', '.ipa', '.ap_', '.dex', '.nib', '.plist',
    # Media
    '.mp4', '.mp3', '.wav', '.ogg', '.mov', '.avi', '.webm', '.m4a',
    # Database/Data
    '.db', '.sqlite', '.sqlite3', '.parquet',
    # Misc
    '.DS_Store', 'Thumbs.db'
}

def is_text_file(path):
    if os.path.getsize(path) > CONFIG['MAX_FILE_SIZE']: return False
    ext = os.path.splitext(path)[1].lower()
    if ext in BINARY_EXTENSIONS: return False
    try:
        with open(path, 'rb') as f:
            chunk = f.read(1024)
            if b'\0' in chunk: return False
    except: return False
    return True

def get_relative_path(path):
    return os.path.relpath(path, CONFIG['ROOT_DIR']).replace(os.sep, '/')

def is_path_excluded(rel_path):
    parts = rel_path.replace("\\", "/").split("/")
    accum = ""
    for part in parts:
        accum = f"{accum}/{part}" if accum else part
        if accum in CONFIG['EXCLUDED_PATHS']: return True
        if part in IGNORE_DIRS: return True
    return False

# --- UI Helpers ---

def get_key():
    """Reads a single keypress with fixed arrow support."""
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

def print_ngrok_setup_guide():
    """Display helpful guide for getting Ngrok token"""
    print(f"\n{C_CYAN}{'='*70}")
    print("  🔑 HOW TO GET YOUR NGROK AUTH TOKEN".center(70))
    print(f"{'='*70}{C_RESET}\n")
    
    print(f"  {C_BOLD}Step 1:{C_RESET} Create a free Ngrok account")
    print(f"  {C_GREY}├─{C_RESET} Go to: {C_BLUE}https://ngrok.com{C_RESET}")
    print(f"  {C_GREY}└─{C_RESET} Sign up with GitHub, Google, or Email\n")
    
    print(f"  {C_BOLD}Step 2:{C_RESET} Get your token")
    print(f"  {C_GREY}├─{C_RESET} Visit: {C_BLUE}https://dashboard.ngrok.com/get-started/your-authtoken{C_RESET}")
    print(f"  {C_GREY}└─{C_RESET} Copy the token (looks like: 2abc123def456...)\n")
    
    print(f"  {C_BOLD}Step 3:{C_RESET} Add token to this script")
    print(f"  {C_GREY}├─{C_RESET} Option A: Paste it in the script variable {C_YELLOW}NGROK_AUTH_TOKEN{C_RESET}")
    print(f"  {C_GREY}├─{C_RESET} Option B: Set environment variable {C_YELLOW}NGROK_AUTHTOKEN{C_RESET}")
    print(f"  {C_GREY}└─{C_RESET} Option C: Paste below when prompted\n")
    
    print(f"  {C_GREEN}💡 Tip:{C_RESET} The free plan is perfect for development!")
    print(f"  {C_GREY}   ✓ No credit card required{C_RESET}")
    print(f"  {C_GREY}   ✓ HTTPS tunnels included{C_RESET}")
    print(f"  {C_GREY}   ✓ Up to 40 connections/minute{C_RESET}\n")
    
    print(f"{C_CYAN}{'='*70}{C_RESET}\n")

def run_interactive_selector():
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
            if p in CONFIG['EXCLUDED_PATHS'] or item in IGNORE_DIRS:
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

            is_excluded = item_rel_path in CONFIG['EXCLUDED_PATHS'] or item in IGNORE_DIRS
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

# --- Routes ---

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        "status": "ok",
        "root": os.path.basename(CONFIG['ROOT_DIR']),
        "capabilities": {"exec": True}
    })

@app.route('/api/scan', methods=['GET'])
@app.route('/api/list', methods=['GET'])
def list_files():
    files = []
    for root, dirs, filenames in os.walk(CONFIG['ROOT_DIR']):
        rel_dir = get_relative_path(root)
        if rel_dir == ".": rel_dir = ""

        # Check exclusion of directory itself
        if rel_dir and is_path_excluded(rel_dir):
            dirs[:] = []
            continue

        # Filter Directories
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.') and not is_path_excluded(os.path.join(rel_dir, d).replace("\\", "/"))]

        for name in filenames:
            if name.startswith('.'): continue

            path = os.path.join(root, name)
            rel_path = get_relative_path(path)

            if is_path_excluded(rel_path): continue

            ext = os.path.splitext(name)[1].lower()
            if ext in BINARY_EXTENSIONS: continue
            if os.path.getsize(path) > CONFIG['MAX_FILE_SIZE']: continue

            files.append({ "path": rel_path, "size": os.path.getsize(path) })

    return jsonify({"files": files})

@app.route('/api/read', methods=['POST'])
def read_file():
    data = request.json
    rel_path = data.get('path')
    if not rel_path: return jsonify({"error": "No path"}), 400
    if is_path_excluded(rel_path): return jsonify({"error": "Excluded"}), 403

    full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
    if not os.path.exists(full_path): return jsonify({"error": "Not found"}), 404
    if not is_text_file(full_path): return jsonify({"error": "Binary/Large"}), 400

    try:
        # Improved reading with fallback encoding
        with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
            return jsonify({"path": rel_path, "content": f.read()})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/batch-read', methods=['POST'])
def batch_read():
    data = request.json
    paths = data.get('paths', [])
    results = []

    for rel_path in paths:
        if is_path_excluded(rel_path): continue
        full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
        if os.path.exists(full_path) and is_text_file(full_path):
            try:
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
    if is_path_excluded(rel_path): return jsonify({"error": "Excluded"}), 403

    full_path = os.path.join(CONFIG['ROOT_DIR'], rel_path)
    try:
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        # Always write utf-8
        with open(full_path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(content)
        return jsonify({"status": "ok", "path": rel_path})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/exec', methods=['POST'])
def exec_cmd():
    data = request.json
    cmd = data.get('command')
    if not cmd: return jsonify({"error": "No command"}), 400
    
    # Security: You might want to restrict this in production!
    try:
        result = subprocess.run(
            cmd, 
            shell=True, 
            cwd=CONFIG['ROOT_DIR'], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE, 
            text=True,
            timeout=30 # 30s timeout
        )
        return jsonify({
            "stdout": result.stdout, 
            "stderr": result.stderr, 
            "code": result.returncode
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # 1. Interactive Selector
    run_interactive_selector()
    
    print(f"\n{C_CYAN}🚀 Starting GenClaude Server...{C_RESET}")
    print(f" Serving: {C_BOLD}{CONFIG['ROOT_DIR']}{C_RESET}")
    
    # 2. Ngrok Setup
    ngrok_url = None
    if HAS_NGROK:
        # Check token in script or env
        token = NGROK_AUTH_TOKEN or os.environ.get("NGROK_AUTHTOKEN")
        
        if not token:
            # Try to load from default config
            try:
                if not conf.get_default().auth_token:
                    print(f"\n{C_YELLOW}⚠️  Ngrok Auth Token Not Found{C_RESET}")
                    print(f" {C_GREY}Need help getting your token? Type 'help' or paste your token below:{C_RESET}")
                    inp = input(f" {C_CYAN}Token (or 'help'):{C_RESET} ").strip()
                    
                    if inp.lower() in ['help', 'h', '?']:
                        print_ngrok_setup_guide()
                        inp = input(f" {C_CYAN}Paste your token here:{C_RESET} ").strip()
                    
                    if inp and inp.lower() not in ['help', 'h', '?', 'skip', '']:
                        token = inp
                    elif not inp or inp.lower() == 'skip':
                        print(f" {C_GREY}→ Skipping Ngrok. Server will run locally only.{C_RESET}")
            except: 
                pass
        
        if token:
            try:
                # CRITICAL: SET TOKEN explicitly for this session
                ngrok.set_auth_token(token)
                
                # Open a HTTP tunnel on the default port 8000
                public_url = ngrok.connect(8000).public_url
                ngrok_url = public_url
                print(f"\n {C_GREEN}✔ Ngrok Tunnel Active!{C_RESET}")
                print(f"   {C_BOLD}{public_url}{C_RESET}")
                print(f"   {C_GREY}(Copy this URL to GenClaude App){C_RESET}")
            except Exception as e:
                print(f"\n {C_RED}✖ Ngrok Error:{C_RESET} {e}")
                print(f" {C_GREY}→ Continuing with local access only...{C_RESET}")
    else:
        print(f"\n {C_YELLOW}ℹ  Ngrok not installed{C_RESET}")
        print(f" {C_GREY}→ Install with: pip install pyngrok{C_RESET}")

    # 3. Print Local URL
    print(f"\n {C_GREEN}✔ Local Access:{C_RESET}")
    print(f"   {C_BOLD}http://127.0.0.1:8000{C_RESET}")
    
    if not ngrok_url and HAS_NGROK:
        print(f"\n {C_GREY}💡 Tip: Set up Ngrok for remote access!{C_RESET}")
        print(f"    Run the script again and type 'help' when prompted.{C_RESET}")

    print(f"\n{C_CYAN}{'='*70}{C_RESET}")
    print(f" {C_GREEN}✨ Server is running! Press Ctrl+C to stop.{C_RESET}")
    print(f"{C_CYAN}{'='*70}{C_RESET}\n")

    # 4. Start Server
    app.run(port=8000, debug=False)