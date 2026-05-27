Name:           lintab
Version:        %{version}
Release:        1%{?dist}
Summary:        Android-to-Linux virtual uinput tablet daemon

License:        GPL-3.0-or-later
URL:            https://github.com/odimsom/LinTab
Source0:        https://github.com/odimsom/LinTab/archive/v%{version}.tar.gz#/LinTab-%{version}.tar.gz

BuildRequires:  rust >= 1.75
BuildRequires:  cargo
BuildRequires:  protobuf-compiler
Requires:       systemd
Recommends:     android-tools

%description
LinTab transforms any Android device into an ultra-low-latency graphics
tablet for Linux using the kernel uinput subsystem.
Supports stylus pressure (8192 levels), tilt, absolute/relative modes,
and an adaptive compensation pipeline for older hardware.

%prep
%autosetup -n LinTab-%{version}

%build
cd core-daemon
CARGO_HOME="%{_builddir}/.cargo" \
  cargo build --frozen --release

%install
cd core-daemon
install -Dm755 target/release/lintab         %{buildroot}%{_bindir}/lintab
install -Dm644 ../packaging/udev/99-lintab.rules \
    %{buildroot}/usr/lib/udev/rules.d/99-lintab.rules
install -Dm644 ../packaging/systemd/lintab.service \
    %{buildroot}/usr/lib/systemd/user/lintab.service
install -Dm644 ../docs/INSTALL-USB.md        %{buildroot}%{_docdir}/%{name}/INSTALL-USB.md
install -Dm644 ../docs/INSTALL-WIFI.md       %{buildroot}%{_docdir}/%{name}/INSTALL-WIFI.md
install -Dm644 ../docs/INSTALL-OLD-TABLET.md %{buildroot}%{_docdir}/%{name}/INSTALL-OLD-TABLET.md

%post
udevadm control --reload-rules 2>/dev/null || true
udevadm trigger 2>/dev/null || true
echo "LinTab instalado. Agrega tu usuario al grupo 'input':"
echo "  sudo usermod -aG input \$USER"
echo "Luego cierra sesión y vuelve a entrar."

%files
%license core-daemon/Cargo.toml
%{_bindir}/lintab
/usr/lib/udev/rules.d/99-lintab.rules
/usr/lib/systemd/user/lintab.service
%{_docdir}/%{name}/

%changelog
* Tue May 27 2026 Francisco Daniel Castro Borrome <borrome941@gmail.com> - 0.3.0-1
- Adaptive input pipeline, hardware profiling, update checker
- Fix rotation ignored in relative mode
- Uniform scale for proportional delta movement
