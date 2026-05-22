# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['D:\\CS_project\\stock-monitor\\hw2\\app\\stock_monitor\\__main__.py'],
    pathex=['D:\\CS_project\\stock-monitor\\hw2\\app'],
    binaries=[],
    datas=[('D:\\CS_project\\stock-monitor\\hw2\\app\\stock_monitor\\web\\static', 'stock_monitor\\web\\static')],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='StockMonitorV2',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='StockMonitorV2',
)
