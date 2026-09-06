#!/usr/bin/env bash
# ==============================================================================
#  CONDA ENVIRONMENT MANAGER (Mobile-First Minimal Edition)
#  Optimized for MobileLinux, Termux, and Linux Terminals (~40 cols)
#
#  Usage:
#    ./conda-manager.sh          (Direct run / subshell)
#    source conda-manager.sh     (Direct shell activation)
#    cman                        (When shortcut is installed)
# ==============================================================================

# Check if script is sourced or executed
(return 0 2>/dev/null) && IS_SOURCED=1 || IS_SOURCED=0

# Clean exit handler
safe_exit() {
    printf "\033[?25h" 2>/dev/null
    stty echo icanon 2>/dev/null
    if [ "$IS_SOURCED" -eq 1 ]; then
        return "${1:-0}" 2>/dev/null || true
    else
        exit "${1:-0}"
    fi
}

trap 'safe_exit 1' INT TERM

# ------------------------------------------------------------------------------
#  Color Palette (Clean & Compatible)
# ------------------------------------------------------------------------------
if [ -t 1 ]; then
    C_RESET="\033[0m"
    C_BOLD="\033[1m"
    C_DIM="\033[2m"
    C_CYAN="\033[1;36m"
    C_GREEN="\033[1;32m"
    C_YELLOW="\033[1;33m"
    C_RED="\033[1;31m"
    C_WHITE="\033[1;37m"
    C_GRAY="\033[0;90m"
    C_INV="\033[7m"
else
    C_RESET=""
    C_BOLD=""
    C_DIM=""
    C_CYAN=""
    C_GREEN=""
    C_YELLOW=""
    C_RED=""
    C_WHITE=""
    C_GRAY=""
    C_INV=""
fi

# ------------------------------------------------------------------------------
#  Conda Initialization
# ------------------------------------------------------------------------------
detect_and_init_conda() {
    if ! command -v conda &>/dev/null; then
        local probe_paths=(
            "$HOME/anaconda3"
            "$HOME/miniconda3"
            "$HOME/miniforge3"
            "$HOME/mambaforge"
            "/opt/conda"
            "/opt/anaconda3"
            "/opt/miniconda3"
            "/data/data/com.termux/files/usr"
            "$PREFIX"
        )
        for p in "${probe_paths[@]}"; do
            if [ -x "$p/condabin/conda" ]; then
                export PATH="$p/condabin:$PATH"
                break
            elif [ -x "$p/bin/conda" ]; then
                export PATH="$p/bin:$PATH"
                break
            fi
        done
    fi

    if ! command -v conda &>/dev/null; then
        echo -e "${C_CYAN}==============================${C_RESET}"
        echo -e "${C_CYAN}    CONDA MANAGER (Mobile)    ${C_RESET}"
        echo -e "${C_CYAN}==============================${C_RESET}"
        echo -e "${C_YELLOW}Conda is not installed yet!${C_RESET}"
        echo ""
        echo "Mobile ARM64 requires Miniforge3"
        echo "(100% compatible with MobileLinux)."
        echo ""
        echo -ne "${C_BOLD}Download & auto-install now? [Y/n]: ${C_RESET}"
        local ans
        read -r ans
        if [[ ! "$ans" =~ ^[nN] ]]; then
            echo ""
            echo -e "${C_CYAN}Downloading Miniforge3 (ARM64)...${C_RESET}"
            local tmp_installer="/tmp/Miniforge3-Linux-aarch64.sh"
            if curl -L -o "$tmp_installer" "https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-Linux-aarch64.sh"; then
                echo ""
                echo -e "${C_CYAN}Installing into $HOME/miniforge3...${C_RESET}"
                bash "$tmp_installer" -b -p "$HOME/miniforge3" -u
                rm -f "$tmp_installer"
                "$HOME/miniforge3/bin/conda" init bash 2>/dev/null || true
                "$HOME/miniforge3/bin/conda" config --set always_copy true 2>/dev/null || true
                export PATH="$HOME/miniforge3/bin:$HOME/miniforge3/condabin:$PATH"
                if [ -f "$HOME/miniforge3/etc/profile.d/conda.sh" ]; then
                    source "$HOME/miniforge3/etc/profile.d/conda.sh"
                fi
                echo ""
                echo -e "${C_GREEN}Miniforge3 installed successfully!${C_RESET}"
                sleep 1
            else
                echo -e "${C_RED}Download failed. Check your connection.${C_RESET}"
                safe_exit 1
            fi
        else
            echo "Exiting."
            safe_exit 1
        fi
    fi

    # PRoot compatibility: enforce always_copy to prevent hardlink (.l2s) corruption
    conda config --set always_copy true 2>/dev/null || true

    if ! type conda | grep -q 'function' 2>/dev/null; then
        local conda_base
        conda_base=$(conda info --base 2>/dev/null)
        if [ -f "$conda_base/etc/profile.d/conda.sh" ]; then
            # shellcheck disable=SC1090
            source "$conda_base/etc/profile.d/conda.sh"
        else
            eval "$(conda shell.bash hook 2>/dev/null)"
        fi
    fi
}

detect_rc_file() {
    if [ -f "$HOME/.bashrc" ]; then
        echo "$HOME/.bashrc"
    elif [ -f "$HOME/.zshrc" ]; then
        echo "$HOME/.zshrc"
    else
        echo "$HOME/.bashrc"
    fi
}

get_current_default_env() {
    local rc_file="$1"
    if [ ! -f "$rc_file" ]; then
        echo "base"
        return
    fi

    local env
    env=$(awk '/# >>> conda-manager default-env >>>/{flag=1; next} /# <<< conda-manager default-env <<</{flag=0} flag && /^[[:space:]]*conda[[:space:]]+activate/{print $3}' "$rc_file" | head -n 1)
    if [ -n "$env" ]; then
        echo "$env"
        return
    fi

    env=$(grep -E "^[[:space:]]*conda[[:space:]]+activate[[:space:]]+" "$rc_file" | grep -v '^[[:space:]]*#' | awk '{print $3}' | tail -n 1)
    if [ -n "$env" ]; then
        echo "$env"
        return
    fi

    echo "base"
}

# ------------------------------------------------------------------------------
#  Environments Cache
# ------------------------------------------------------------------------------
ENV_NAMES=()
ENV_PATHS=()
ENV_ACTIVE=()
ENV_PYTHON=()

refresh_environments() {
    ENV_NAMES=()
    ENV_PATHS=()
    ENV_ACTIVE=()
    ENV_PYTHON=()

    local line
    while IFS= read -r line; do
        [[ "$line" =~ ^#.*$ ]] && continue
        [[ -z "${line// }" ]] && continue

        local is_act=0
        [[ "$line" =~ \* ]] && is_act=1

        local clean_line="${line/\*/}"
        local name path
        name=$(echo "$clean_line" | awk '{print $1}')
        path=$(echo "$clean_line" | awk '{print $NF}')

        if [ "$name" = "$path" ]; then
            name="[$(basename "$path")]"
        fi

        local py_ver="N/A"
        if [ -x "$path/bin/python" ]; then
            py_ver=$("$path/bin/python" -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}")' 2>/dev/null || echo "Unknown")
        fi

        ENV_NAMES+=("$name")
        ENV_PATHS+=("$path")
        ENV_ACTIVE+=("$is_act")
        ENV_PYTHON+=("$py_ver")
    done < <(conda env list 2>/dev/null)
}

# ------------------------------------------------------------------------------
#  Keyboard Input Engine (Arrow keys & Numbers)
# ------------------------------------------------------------------------------
read_key() {
    local char=""
    local rest=""

    if ! IFS= read -rsn1 char 2>/dev/null; then
        echo "EOF"
        return
    fi

    if [[ "$char" == $'\x1b' ]]; then
        read -rsn2 -t 0.08 rest 2>/dev/null
        char+="$rest"
    fi

    case "$char" in
        $'\x1b[A'|$'\x1bOA'|"k"|"K") echo "UP" ;;
        $'\x1b[B'|$'\x1bOB'|"j"|"J") echo "DOWN" ;;
        $'\x1b[C'|$'\x1bOC') echo "RIGHT" ;;
        $'\x1b[D'|$'\x1bOD') echo "LEFT" ;;
        "") echo "ENTER" ;;
        " ") echo "SPACE" ;;
        "q"|"Q") echo "QUIT" ;;
        $'\x1b') echo "ESC" ;;
        [0-9]) echo "NUM_$char" ;;
        *) echo "$char" ;;
    esac
}

press_enter_to_continue() {
    echo ""
    echo -e "${C_GRAY}Press [ENTER] to continue...${C_RESET}"
    local dummy
    read -r dummy 2>/dev/null || safe_exit 0
}

# ------------------------------------------------------------------------------
#  Mobile-Friendly Menu Selector
# ------------------------------------------------------------------------------
SELECTED_INDEX=-1
interactive_select() {
    local title="$1"
    local default_idx="$2"
    shift 2
    local options=("$@")
    local count=${#options[@]}

    if [ "$count" -eq 0 ]; then
        SELECTED_INDEX=-1
        return
    fi

    local current=$default_idx
    [ "$current" -lt 0 ] && current=0
    [ "$current" -ge "$count" ] && current=$((count - 1))

    printf "\033[?25l" 2>/dev/null

    local first_draw=1
    local lines_drawn=0

    while true; do
        if [ "$first_draw" -eq 0 ] && [ "$lines_drawn" -gt 0 ]; then
            printf "\033[%dA\033[J" "$lines_drawn"
        fi
        first_draw=0
        lines_drawn=0

        if [ -n "$title" ]; then
            echo -e "${C_CYAN}${title}${C_RESET}"
            ((lines_drawn++))
        fi
        echo -e "${C_GRAY}(Use Up/Down + Enter, or 1-${count})${C_RESET}"
        ((lines_drawn++))

        for i in "${!options[@]}"; do
            local num="$((i + 1))"
            if [ "$i" -eq "$current" ]; then
                echo -e "${C_GREEN}${C_BOLD}> [${num}] ${options[$i]}${C_RESET}"
            else
                echo -e "  [${num}] ${options[$i]}"
            fi
            ((lines_drawn++))
        done

        local key
        key=$(read_key)

        case "$key" in
            UP)
                ((current--))
                [ "$current" -lt 0 ] && current=$((count - 1))
                ;;
            DOWN)
                ((current++))
                [ "$current" -ge "$count" ] && current=0
                ;;
            ENTER|SPACE)
                SELECTED_INDEX=$current
                break
                ;;
            QUIT|ESC|EOF)
                SELECTED_INDEX=-1
                break
                ;;
            NUM_*)
                local digit="${key#NUM_}"
                if [ "$digit" -ge 1 ] && [ "$digit" -le "$count" ]; then
                    SELECTED_INDEX=$((digit - 1))
                    break
                elif [ "$digit" -eq 0 ]; then
                    SELECTED_INDEX=-1
                    break
                fi
                ;;
        esac
    done

    printf "\033[?25h" 2>/dev/null
}

# ------------------------------------------------------------------------------
#  Clean Mobile Header (< 34 chars wide)
# ------------------------------------------------------------------------------
render_header() {
    clear 2>/dev/null || printf "\033[H\033[2J"
    local rc_file
    rc_file=$(detect_rc_file)
    local default_env
    default_env=$(get_current_default_env "$rc_file")
    local active_env="${CONDA_DEFAULT_ENV:-None}"
    local total_envs=${#ENV_NAMES[@]}

    echo -e "${C_CYAN}==============================${C_RESET}"
    echo -e "${C_CYAN}    CONDA MANAGER (Mobile)    ${C_RESET}"
    echo -e "${C_CYAN}==============================${C_RESET}"
    echo -e "Active:  ${C_GREEN}${active_env}${C_RESET}"
    echo -e "Default: ${C_YELLOW}${default_env}${C_RESET}"
    echo -e "Total:   ${C_WHITE}${total_envs} environment(s)${C_RESET}"
    echo -e "${C_GRAY}------------------------------${C_RESET}"
}

# ------------------------------------------------------------------------------
#  Action 1: List Environments (Clean Mobile Cards)
# ------------------------------------------------------------------------------
action_list_envs() {
    render_header
    refresh_environments

    local rc_file
    rc_file=$(detect_rc_file)
    local default_env
    default_env=$(get_current_default_env "$rc_file")

    echo -e "${C_BOLD}Installed Environments:${C_RESET}"
    echo ""

    for i in "${!ENV_NAMES[@]}"; do
        local name="${ENV_NAMES[$i]}"
        local path="${ENV_PATHS[$i]}"
        local py="${ENV_PYTHON[$i]}"
        local is_act="${ENV_ACTIVE[$i]}"

        local badges=""
        if [ "$is_act" -eq 1 ] || [ "$name" = "$CONDA_DEFAULT_ENV" ]; then
            badges="${C_GREEN}*ACTIVE${C_RESET} "
        fi
        if [ "$name" = "$default_env" ]; then
            badges+="${C_YELLOW}[DEFAULT]${C_RESET}"
        fi

        local size="N/A"
        if [ -d "$path" ]; then
            size=$(timeout 1.5 du -sh "$path" 2>/dev/null | awk '{print $1}' || echo "N/A")
            [ -z "$size" ] && size="N/A"
        fi

        echo -e "${C_BOLD}[$((i + 1))] ${C_WHITE}${name}${C_RESET} ${badges}"
        echo -e "    ${C_GRAY}Python:${C_RESET} ${py}"
        echo -e "    ${C_GRAY}Size:  ${C_RESET} ${size}"
        echo ""
    done

    echo -e "${C_GRAY}------------------------------${C_RESET}"
    press_enter_to_continue
}

# ------------------------------------------------------------------------------
#  Action 2: Activate Environment
# ------------------------------------------------------------------------------
action_activate_env() {
    render_header
    refresh_environments

    local options=()
    local default_idx=0
    for i in "${!ENV_NAMES[@]}"; do
        local name="${ENV_NAMES[$i]}"
        local py="${ENV_PYTHON[$i]}"
        local extra=""
        if [ "${ENV_ACTIVE[$i]}" -eq 1 ] || [ "$name" = "$CONDA_DEFAULT_ENV" ]; then
            extra=" ${C_GREEN}*Active${C_RESET}"
            default_idx=$i
        fi
        options+=("${name} ${C_GRAY}(Py ${py})${C_RESET}${extra}")
    done

    interactive_select "Activate Environment:" "$default_idx" "${options[@]}"

    if [ "$SELECTED_INDEX" -lt 0 ]; then
        echo -e "${C_YELLOW}Cancelled.${C_RESET}"
        sleep 0.6
        return
    fi

    local target_env="${ENV_NAMES[$SELECTED_INDEX]}"
    echo ""
    echo -e "${C_CYAN}Activating: ${C_BOLD}${target_env}${C_RESET}..."

    if [ "$IS_SOURCED" -eq 1 ]; then
        conda activate "$target_env"
        echo -e "${C_GREEN}Activated '${target_env}' in this shell!${C_RESET}"
        safe_exit 0
    else
        echo -e "${C_GREEN}Starting subshell with '${target_env}'...${C_RESET}"
        echo -e "${C_GRAY}(Type 'exit' to return)${C_RESET}"
        echo ""
        exec bash --init-file <(echo "
            [ -f ~/.bashrc ] && source ~/.bashrc 2>/dev/null
            conda activate \"$target_env\" 2>/dev/null
            echo -e \"${C_GREEN}Now active: ($target_env)${C_RESET}\"
        ") -i
    fi
}

# ------------------------------------------------------------------------------
#  Action 3: Set Default Startup Environment
# ------------------------------------------------------------------------------
action_set_default_env() {
    render_header
    refresh_environments

    local rc_file
    rc_file=$(detect_rc_file)
    local cur_default
    cur_default=$(get_current_default_env "$rc_file")

    local options=()
    local default_idx=0
    for i in "${!ENV_NAMES[@]}"; do
        local name="${ENV_NAMES[$i]}"
        local extra=""
        if [ "$name" = "$cur_default" ]; then
            extra=" ${C_YELLOW}[Default]${C_RESET}"
            default_idx=$i
        fi
        options+=("${name}${extra}")
    done

    interactive_select "Set Default on Startup:" "$default_idx" "${options[@]}"

    if [ "$SELECTED_INDEX" -lt 0 ]; then
        echo -e "${C_YELLOW}Cancelled.${C_RESET}"
        sleep 0.6
        return
    fi

    local target_env="${ENV_NAMES[$SELECTED_INDEX]}"

    echo ""
    sed -i '/# >>> conda-manager default-env >>>/,/# <<< conda-manager default-env <<</d' "$rc_file"
    sed -i '/^[[:space:]]*conda[[:space:]]\+activate.*# CONDA_DEFAULT_ENV/d' "$rc_file"

    if [ "$target_env" != "base" ]; then
        cat << BLOCK >> "$rc_file"

# >>> conda-manager default-env >>>
conda activate $target_env
# <<< conda-manager default-env <<<
BLOCK
    fi

    # Clean empty lines
    cat -s "$rc_file" > "$rc_file.tmp" && mv "$rc_file.tmp" "$rc_file"

    echo -e "${C_GREEN}Done! Default env is now: ${target_env}${C_RESET}"
    echo -e "${C_GRAY}Next time terminal opens, '${target_env}' will start.${C_RESET}"
    press_enter_to_continue
}

# ------------------------------------------------------------------------------
#  Action 4: Create Environment (Minimal Wizard)
# ------------------------------------------------------------------------------
action_create_env() {
    render_header
    echo -e "${C_BOLD}Create New Environment:${C_RESET}"
    echo ""

    local env_name=""
    while true; do
        echo -ne "Name: "
        read -r env_name
        env_name=$(echo "$env_name" | tr -d '[:space:]')

        if [ -z "$env_name" ]; then
            echo -e "${C_RED}Name cannot be empty.${C_RESET}"
            continue
        fi

        refresh_environments
        local exists=0
        for e in "${ENV_NAMES[@]}"; do
            if [ "$e" = "$env_name" ]; then
                exists=1
                break
            fi
        done

        if [ "$exists" -eq 1 ]; then
            echo -e "${C_RED}Environment '$env_name' already exists.${C_RESET}"
            continue
        fi

        break
    done

    echo ""
    local py_options=(
        "3.10 (Recommended for AI)"
        "3.11 (Fast & Modern)"
        "3.12 (Latest Standard)"
        "Custom Version..."
    )
    interactive_select "Python Version:" 0 "${py_options[@]}"

    local py_ver="3.10"
    case "$SELECTED_INDEX" in
        0) py_ver="3.10" ;;
        1) py_ver="3.11" ;;
        2) py_ver="3.12" ;;
        3)
            echo -ne "Enter version (e.g. 3.9): "
            read -r py_ver
            [ -z "$py_ver" ] && py_ver="3.10"
            ;;
        *)
            echo -e "${C_YELLOW}Cancelled.${C_RESET}"
            sleep 0.6
            return
            ;;
    esac

    echo ""
    echo "Extra packages (optional, e.g. numpy)"
    echo -ne "[Press Enter to skip]: "
    local extra_pkgs=""
    read -r extra_pkgs

    echo ""
    echo -e "${C_GRAY}------------------------------${C_RESET}"
    echo -e "Name:    ${C_WHITE}${env_name}${C_RESET}"
    echo -e "Python:  ${C_YELLOW}${py_ver}${C_RESET}"
    [ -n "$extra_pkgs" ] && echo -e "Extra:   ${extra_pkgs}"
    echo -e "${C_GRAY}------------------------------${C_RESET}"
    echo -ne "Create now? [Y/n]: "
    local confirm
    read -r confirm
    if [[ "$confirm" =~ ^[nN] ]]; then
        echo -e "${C_YELLOW}Cancelled.${C_RESET}"
        sleep 0.6
        return
    fi

    echo ""
    echo -e "${C_CYAN}Creating environment... please wait.${C_RESET}"
    echo ""

    # shellcheck disable=SC2086
    if conda create --copy -n "$env_name" python="$py_ver" $extra_pkgs -y; then
        echo ""
        echo -e "${C_GREEN}Environment '$env_name' created!${C_RESET}"

        echo ""
        echo -ne "Set as default on startup? [y/N]: "
        local set_def
        read -r set_def
        if [[ "$set_def" =~ ^[yY] ]]; then
            local rc_file
            rc_file=$(detect_rc_file)
            sed -i '/# >>> conda-manager default-env >>>/,/# <<< conda-manager default-env <<</d' "$rc_file"
            cat << BLOCK >> "$rc_file"

# >>> conda-manager default-env >>>
conda activate $env_name
# <<< conda-manager default-env <<<
BLOCK
            cat -s "$rc_file" > "$rc_file.tmp" && mv "$rc_file.tmp" "$rc_file"
            echo -e "${C_GREEN}Set '$env_name' as default startup env.${C_RESET}"
        fi

        echo ""
        echo -ne "Activate right now? [Y/n]: "
        local act_now
        read -r act_now
        if [[ ! "$act_now" =~ ^[nN] ]]; then
            if [ "$IS_SOURCED" -eq 1 ]; then
                conda activate "$env_name"
                echo -e "${C_GREEN}Activated '${env_name}'!${C_RESET}"
                safe_exit 0
            else
                exec bash --init-file <(echo "
                    [ -f ~/.bashrc ] && source ~/.bashrc 2>/dev/null
                    conda activate \"$env_name\" 2>/dev/null
                    echo -e \"${C_GREEN}Now active: ($env_name)${C_RESET}\"
                ") -i
            fi
        fi
    else
        echo -e "${C_RED}Failed to create environment.${C_RESET}"
    fi

    press_enter_to_continue
}

# ------------------------------------------------------------------------------
#  Action 5: Delete Environment
# ------------------------------------------------------------------------------
action_delete_env() {
    render_header
    refresh_environments

    local options=()
    local valid_indices=()
    for i in "${!ENV_NAMES[@]}"; do
        local name="${ENV_NAMES[$i]}"
        if [ "$name" = "base" ]; then
            continue
        fi
        local extra=""
        if [ "${ENV_ACTIVE[$i]}" -eq 1 ] || [ "$name" = "$CONDA_DEFAULT_ENV" ]; then
            extra=" ${C_RED}(Active)${C_RESET}"
        fi
        options+=("${name}${extra}")
        valid_indices+=("$i")
    done

    if [ ${#options[@]} -eq 0 ]; then
        echo -e "${C_YELLOW}No removable environments found.${C_RESET}"
        press_enter_to_continue
        return
    fi

    interactive_select "Delete Environment:" 0 "${options[@]}"

    if [ "$SELECTED_INDEX" -lt 0 ]; then
        echo -e "${C_YELLOW}Cancelled.${C_RESET}"
        sleep 0.6
        return
    fi

    local real_idx="${valid_indices[$SELECTED_INDEX]}"
    local target_env="${ENV_NAMES[$real_idx]}"

    echo ""
    echo -e "${C_RED}Warning: Will delete '${target_env}'!${C_RESET}"
    echo -ne "Type '${target_env}' to confirm: "
    local confirmation
    read -r confirmation

    if [ "$confirmation" != "$target_env" ]; then
        echo -e "${C_YELLOW}Cancelled (name mismatch).${C_RESET}"
        sleep 1
        return
    fi

    echo ""
    echo -e "${C_CYAN}Deleting '${target_env}'...${C_RESET}"

    if [ "$target_env" = "$CONDA_DEFAULT_ENV" ]; then
        conda activate base 2>/dev/null || true
    fi

    if conda env remove -n "$target_env" -y; then
        echo -e "${C_GREEN}Deleted '${target_env}'.${C_RESET}"
        local rc_file
        rc_file=$(detect_rc_file)
        local cur_default
        cur_default=$(get_current_default_env "$rc_file")
        if [ "$cur_default" = "$target_env" ]; then
            sed -i '/# >>> conda-manager default-env >>>/,/# <<< conda-manager default-env <<</d' "$rc_file"
            cat -s "$rc_file" > "$rc_file.tmp" && mv "$rc_file.tmp" "$rc_file"
            echo -e "${C_GREEN}Reset default startup back to 'base'.${C_RESET}"
        fi
    else
        echo -e "${C_RED}Failed to delete.${C_RESET}"
    fi

    press_enter_to_continue
}

# ------------------------------------------------------------------------------
#  Action 6: Clean Conda Cache
# ------------------------------------------------------------------------------
action_clean_cache() {
    render_header
    echo -e "${C_BOLD}Clean Conda Cache & Free Storage${C_RESET}"
    echo "Removes unused packages and tarballs."
    echo ""
    echo -ne "Run 'conda clean -a -y'? [Y/n]: "
    local ans
    read -r ans
    if [[ ! "$ans" =~ ^[nN] ]]; then
        echo ""
        conda clean -a -y
        echo ""
        echo -e "${C_GREEN}Storage cleaned!${C_RESET}"
    fi
    press_enter_to_continue
}

# ------------------------------------------------------------------------------
#  Action 7: Install 'cman' Shortcut
# ------------------------------------------------------------------------------
action_install_shortcut() {
    render_header
    local rc_file
    rc_file=$(detect_rc_file)

    local script_path
    if [ -n "${BASH_SOURCE[0]}" ]; then
        script_path=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")
    else
        script_path=$(realpath "$0" 2>/dev/null || echo "$HOME/conda-manager.sh")
    fi

    echo -e "${C_BOLD}Install 'cman' Shortcut:${C_RESET}"
    echo "Allows opening this manager anytime"
    echo "by typing 'cman' in terminal."
    echo ""
    echo -ne "Install shortcut to ${rc_file}? [Y/n]: "
    local ans
    read -r ans
    if [[ ! "$ans" =~ ^[nN] ]]; then
        sed -i '/alias cman=/d' "$rc_file"
        echo "alias cman=\"source '$script_path'\"" >> "$rc_file"
        echo ""
        echo -e "${C_GREEN}Done! Type 'cman' anytime to run.${C_RESET}"
    fi
    press_enter_to_continue
}

# ------------------------------------------------------------------------------
#  Main Menu Loop (Minimal & Compact)
# ------------------------------------------------------------------------------
main_menu() {
    detect_and_init_conda

    local menu_options=(
        "List Environments"
        "Activate Environment"
        "Set Default on Startup"
        "Create Environment"
        "Delete Environment"
        "Clean Cache & Storage"
        "Add 'cman' Shortcut"
        "Exit"
    )

    local selected_menu=0

    while true; do
        refresh_environments
        render_header

        interactive_select "Select Option:" "$selected_menu" "${menu_options[@]}"

        case "$SELECTED_INDEX" in
            0) selected_menu=0; action_list_envs ;;
            1) selected_menu=1; action_activate_env ;;
            2) selected_menu=2; action_set_default_env ;;
            3) selected_menu=3; action_create_env ;;
            4) selected_menu=4; action_delete_env ;;
            5) selected_menu=5; action_clean_cache ;;
            6) selected_menu=6; action_install_shortcut ;;
            7|-1)
                echo ""
                echo -e "${C_GREEN}Bye!${C_RESET}"
                safe_exit 0
                ;;
        esac
    done
}

# ------------------------------------------------------------------------------
#  Run
# ------------------------------------------------------------------------------
main_menu
