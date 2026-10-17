#!/usr/bin/env bash
# C-50: Debian 13 clean VM runner for testing installed .deb packages via libvirt/KVM.
# Usage:
#   scripts/lab/linux/vm.sh init
#   scripts/lab/linux/vm.sh run --deb products/linux-desktop/dist/photo-selector-linux_*.deb --out <run_dir>
set -euo pipefail

VM_NAME="pstlab-deb13"
VM_DIR="$HOME/PSTLab/vm"
BASE_IMG="$VM_DIR/debian-13-genericcloud-amd64.qcow2"
OVERLAY_IMG="$VM_DIR/${VM_NAME}.qcow2"
VM_USER="lab"
VM_HOST="${PSTLAB_VM_HOST:-192.168.122.50}"

check_kvm() {
  if [[ ! -e /dev/kvm ]]; then
    echo "error: /dev/kvm not available on this host." >&2
    exit 2
  fi
  if ! command -v virsh >/dev/null 2>&1; then
    echo "error: virsh (libvirt-clients) not installed." >&2
    exit 2
  fi
}

cmd="${1:-help}"
shift || true

case "$cmd" in
  init)
    check_kvm
    mkdir -p "$VM_DIR"
    if [[ ! -f "$BASE_IMG" ]]; then
      echo "Download Debian 13 genericcloud qcow2 to $BASE_IMG and verify SHA512 before running init." >&2
      exit 1
    fi
    qemu-img create -f qcow2 -b "$BASE_IMG" -F qcow2 "$OVERLAY_IMG" 20G
    cat > "$VM_DIR/user-data" <<EOF
#cloud-config
users:
  - name: $VM_USER
    sudo: ALL=(ALL) NOPASSWD:ALL
    shell: /bin/bash
packages:
  - task-gnome-desktop
  - xvfb
  - xdotool
  - imagemagick
  - dbus-x11
  - at-spi2-core
  - openssh-server
EOF
    virt-install --import --name "$VM_NAME" --memory 4096 --vcpus 2 \
      --disk "$OVERLAY_IMG" --os-variant debian12 \
      --cloud-init "user-data=$VM_DIR/user-data" --noautoconsole
    echo "Waiting for cloud-init on $VM_NAME, then run: virsh snapshot-create-as $VM_NAME clean"
    ;;
  run)
    check_kvm
    DEB_PATH=""
    OUT_DIR=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --deb) DEB_PATH="$2"; shift 2 ;;
        --out) OUT_DIR="$2"; shift 2 ;;
        *) shift ;;
      esac
    done
    if [[ -z "$DEB_PATH" || -z "$OUT_DIR" ]]; then
      echo "usage: vm.sh run --deb <path.deb> --out <run_dir>" >&2
      exit 2
    fi
    virsh snapshot-revert "$VM_NAME" clean
    scp "$DEB_PATH" "${VM_USER}@${VM_HOST}:/tmp/photo-selector-linux.deb"
    ssh "${VM_USER}@${VM_HOST}" "sudo apt-get update && sudo apt-get install -y /tmp/photo-selector-linux.deb"
    ssh "${VM_USER}@${VM_HOST}" "mkdir -p ~/pstlab && python3 ~/pstlab/scripts/lab/lab.py run --product linux --installed --out ~/pstlab/out"
    scp -r "${VM_USER}@${VM_HOST}:~/pstlab/out/linux" "$OUT_DIR/"
    ;;
  *)
    echo "usage: $0 {init|run --deb <deb> --out <dir>}" >&2
    exit 2
    ;;
esac
