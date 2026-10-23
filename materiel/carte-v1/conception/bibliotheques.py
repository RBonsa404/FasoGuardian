"""Lecture des bibliothèques KiCad (symboles et empreintes) : celles de KiCad et celle du projet (« fg »)."""
from __future__ import annotations

import os
import re
from functools import lru_cache
from pathlib import Path

ICI = Path(__file__).resolve().parent
PROJET = ICI.parent
KICAD = Path(os.environ.get('KICAD_RACINE', r'C:\Users\rachi\AppData\Local\Programs\KiCad\10.0'))
SYMBOLES = KICAD / 'share' / 'kicad' / 'symbols'
EMPREINTES = KICAD / 'share' / 'kicad' / 'footprints'
BIBLIO_FG = PROJET / 'bibliotheque'


def fichier_symboles(bibliotheque: str) -> Path:
    return BIBLIO_FG / 'fg.kicad_sym' if bibliotheque == 'fg' else SYMBOLES / f'{bibliotheque}.kicad_sym'


def dossier_empreintes(bibliotheque: str) -> Path:
    return BIBLIO_FG / 'fg.pretty' if bibliotheque == 'fg' else EMPREINTES / f'{bibliotheque}.pretty'


def bloc(texte: str, debut: int) -> str:
    """Expression S complète qui commence à la parenthèse ouvrante d'indice `debut`."""
    profondeur = 0
    dans_chaine = False
    i = debut
    while i < len(texte):
        c = texte[i]
        if dans_chaine:
            if c == '\\':
                i += 1
            elif c == '"':
                dans_chaine = False
        elif c == '"':
            dans_chaine = True
        elif c == '(':
            profondeur += 1
        elif c == ')':
            profondeur -= 1
            if profondeur == 0:
                return texte[debut:i + 1]
        i += 1
    raise ValueError('expression non fermée')


@lru_cache(maxsize=None)
def _texte(chemin: Path) -> str:
    return chemin.read_text(encoding='utf-8')


@lru_cache(maxsize=None)
def symbole(identifiant: str) -> str:
    """Définition du symbole « bibliothèque:nom », héritage (« extends ») résolu."""
    bibliotheque, nom = identifiant.split(':', 1)
    texte = _texte(fichier_symboles(bibliotheque))
    m = re.search(r'\n\s*\(symbol "%s"\s' % re.escape(nom), texte)
    if not m:
        raise KeyError(f'symbole introuvable : {identifiant}')
    definition = bloc(texte, m.start() + texte[m.start():].index('('))
    parent = re.search(r'\(extends "([^"]+)"\)', definition)
    if parent:
        raise ValueError(f'{identifiant} hérite de {parent.group(1)} : utiliser le symbole parent')
    return definition


def broches_symbole(identifiant: str) -> dict[str, dict]:
    """Broches du symbole : numéro → nom, type électrique, position (mm, repère du symbole), orientation."""
    definition = symbole(identifiant)
    nom_symbole = re.escape(identifiant.split(':', 1)[1])
    # Unité de chaque broche : sous-symbole « nom_U_S » qui la contient (0 = commune à toutes les unités).
    sous = [(m.start(), m.start() + len(bloc(definition, m.start())), int(m.group(1)))
            for m in re.finditer(r'\(symbol "%s_(\d+)_\d+"' % nom_symbole, definition)]
    broches = {}
    for m in re.finditer(r'\(pin (\w+) (\w+)\s*\(at ([-\d.]+) ([-\d.]+) ([-\d.]+)\)', definition):
        corps = bloc(definition, m.start())
        unite = next((u for debut, fin, u in sous if debut < m.start() < fin), 1)
        nom = re.search(r'\(name "([^"]*)"', corps).group(1)
        numero = re.search(r'\(number "([^"]*)"', corps).group(1)
        longueur = float(re.search(r'\(length ([-\d.]+)\)', corps).group(1))
        broches[numero] = {'nom': nom, 'type': m.group(1), 'x': float(m.group(3)), 'y': float(m.group(4)),
                           'angle': float(m.group(5)), 'longueur': longueur, 'unite': unite}
    return broches


@lru_cache(maxsize=None)
def empreinte(identifiant: str) -> str:
    bibliotheque, nom = identifiant.split(':', 1)
    chemin = dossier_empreintes(bibliotheque) / f'{nom}.kicad_mod'
    if not chemin.exists():
        raise KeyError(f'empreinte introuvable : {identifiant}')
    return _texte(chemin)


def plots_empreinte(identifiant: str) -> set[str]:
    """Numéros des plots électriques (les trous de fixation sans numéro sont ignorés)."""
    texte = empreinte(identifiant)
    plots = set()
    for m in re.finditer(r'\(pad "?([^"\s)]*)"? (\w+)', texte):
        if m.group(1) and m.group(2) != 'np_thru_hole':
            plots.add(m.group(1))
    return plots
