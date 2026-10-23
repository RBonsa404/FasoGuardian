"""Impédance d'un guide coplanaire à plan de masse (CPWG), formule de Wadell (« Transmission Line Design
Handbook », § 3.4.3), par intégrales elliptiques complètes calculées par moyenne arithmético-géométrique.

Usage : python outils/impedance.py — affiche la largeur de piste donnant 50 Ω pour plusieurs fentes.
Les valeurs sont à confirmer avec le calculateur d'impédance du façonnier pour son empilement exact.
"""
from math import cosh, pi, sqrt, tanh


def K(k: float) -> float:
    """Intégrale elliptique complète de première espèce K(k)."""
    a, b = 1.0, sqrt(1 - k * k)
    for _ in range(40):
        a, b = (a + b) / 2, sqrt(a * b)
    return pi / (2 * a)


def cpwg(w: float, s: float, h: float, er: float) -> tuple[float, float]:
    """Impédance (Ω) et permittivité effective : piste w, fente s, diélectrique h (mêmes unités)."""
    k = w / (w + 2 * s)
    k3 = tanh(pi * w / (4 * h)) / tanh(pi * (w + 2 * s) / (4 * h))
    kp, k3p = sqrt(1 - k * k), sqrt(1 - k3 * k3)
    q = (K(kp) * K(k3)) / (K(k) * K(k3p))
    eeff = (1 + er * q) / (1 + q)
    z = 60 * pi / sqrt(eeff) / (K(k) / K(kp) + K(k3) / K(k3p))
    return z, eeff


if __name__ == '__main__':
    H, ER = 0.0994, 4.1  # JLC04101H-3313 : préimprégné 3313 entre L1 et L2 (mm), permittivité
    for s in (0.10, 0.127, 0.15, 0.20):
        w = min((x / 1000 for x in range(80, 600)), key=lambda x: abs(cpwg(x, s, H, ER)[0] - 50))
        z, e = cpwg(w, s, H, ER)
        print(f'fente {s:.3f} mm : piste {w:.3f} mm → {z:.1f} Ω (εeff {e:.2f})')
