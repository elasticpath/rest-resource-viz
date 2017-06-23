#!/usr/bin/env bash

set -ex

######################
# PLATFORM DETECTION #
######################

platform=

OS="$(uname)"
case $OS in
    "Linux")
        if [ "$(uname -m)" != "x86_64" ]; then
            echo "Linux is supported, but only for x86_64 architectures at the moment."
            exit 3
        fi
        platform="Linux_x64"
        ;;
    "Darwin")
        platform="Mac"
        ;;
    *)
        if [[ "$OSTYPE" == "cygwin" ]]; then
            # POSIX compatibility layer and Linux environment emulation for Windows
            # This is untested, sorry about that.
            platform="Win"
        elif [[ "$OSTYPE" == "msys" ]]; then
            # Lightweight shell and GNU utilities compiled for Windows (part of MinGW)
            # This is untested, sorry about that.
            platform="Win"
        elif [[ "$OSTYPE" == "win32" ]]; then
            # This is untested, sorry about that.
            platform="Win"
        else
            echo "Cannot detect the target OS platform."
            exit 2
        fi
        ;;
esac

if [ $platform == "Win" ]; then
    echo "Windows is not supported at the moment."
    exit 3
fi

################
# SCRIPT START #
################

install_dir=
profile_dir=
chromium_open_url=
chromium_user_dir=dirac-profile

installed_file_name=.dirac-installed
installed_file_dir=$(find . -name $installed_file_name)

if [ ! -s "$installed_file_dir" ]; then

    echo "Installing Chromium Canary for Dirac..."

    temp_dir=$(mktemp -d dirac.XXXXXXX)

    profile_dir=$temp_dir/$chromium_user_dir
    mkdir -p "$profile_dir"

    github_coord="binaryage/dirac"
    dirac_repo="https://github.com/$github_coord"
    dirac_raw_repo="https://raw.githubusercontent.com/$github_coord"



    dirac_release_tag=$(curl -s https://api.github.com/repos/$github_coord/releases/latest | grep tag_name | cut -d ":" -f 2 | grep -Eoi '\"(.*)\"' | sed 's/[ \"]//g')
    dirac_release_notes=$(curl -s https://api.github.com/repos/binaryage/dirac/releases/latest | grep -E '\"body\"')

    echo "Fetching build for $platform..."
    link=$(curl -s https://api.github.com/repos/binaryage/dirac/releases/latest | grep -E '\"body\"' | grep -Eo "\[$platform\]\(.*\)" | cut -d "|" -f 1 | grep -Po '\(.*\)' | sed 's/[ \)\(]//g')

    if [ -z "$link" ]; then
        echo "Cannot detect the Chromium Canary link for $platform."
        exit 4
    fi

    echo "Exploding to $install_dir..."
    old_dir=$(pwd)
    cd $temp_dir

    if curl -LOk $link; then
        file_name=$(basename $link)
        unzip "$file_name"
        rm -v "$file_name"
        install_dir=$temp_dir/$(echo $file_name | sed 's/\..*$//g')
    else
        echo "Cannot fetch Chromium Canary from $link."
        exit 5
    fi
    cd "$old_dir"

    echo "Dirac $dirac_release_tag - Chromium $dirac_chromium_version for $platform" > "$temp_dir/$installed_file_name"
    chromium_open_url="https://chrome.google.com/webstore/detail/dirac-devtools/kbkdngfljkchidcjpnfcgcokkbhlkogi?hl=en"
else
    base_dir=$(dirname "$installed_file_dir")
    install_dir=$(find $base_dir -type d -name "chrome*")
    echo "Chromium Canary for Dirac detected in $install_dir"
    profile_dir="$base_dir/dirac-profile"
fi

if [ -z "$install_dir" ]; then
    echo "Oh noes! This should never happen and probably is a bug."
    exit 254
fi

chromium_port=9222
echo "Launching Chromium with remote debugging on port $chromium_port..."
"$install_dir/chrome" --remote-debugging-port=$chromium_port --no-first-run --user-data-dir="$profile_dir" $chromium_open_url
