package com.zerotv.next;

enum ThemePalette {
    AURORA("Aurora", 0xFF0B171C, 0xFF163331, 0xFF9AF2D2, 0xFF438B9E),
    MIDNIGHT("Midnight", 0xFF080C19, 0xFF192748, 0xFFB9D4FF, 0xFF7662DB),
    AMETHYST("Amethyst", 0xFF130B20, 0xFF33204A, 0xFFE1C1FF, 0xFF9770EB),
    ROSEWOOD("Rosewood", 0xFF190D19, 0xFF3A2034, 0xFFFFB9D2, 0xFFC75E85),
    EMBER("Ember", 0xFF1B100E, 0xFF402820, 0xFFFFD0A1, 0xFFC87953);

    final String label;
    final int base;
    final int panel;
    final int accent;
    final int aurora;

    ThemePalette(String label, int base, int panel, int accent, int aurora) {
        this.label = label;
        this.base = base;
        this.panel = panel;
        this.accent = accent;
        this.aurora = aurora;
    }
}
