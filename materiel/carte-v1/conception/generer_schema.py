"""Étape 2 : génère le projet KiCad et ses feuilles de schéma à partir de la netlist de référence.

Une feuille par bloc fonctionnel ; chaque broche porte une étiquette globale du nom de son réseau (les réseaux
traversent les feuilles), chaque broche libre une croix de non-connexion. Les identifiants sont stables (dérivés des
références) : le circuit imprimé reprend les mêmes chemins, ce qui permet le contrôle de parité schéma ↔ carte.

Usage : python generer_schema.py
"""
from __future__ import annotations

import json
import re
import uuid
from pathlib import Path

import bibliotheques as bib
from carte import ALIMENTATIONS, COMPOSANTS, FEUILLES, TITRES

NOM = 'fasoguardian-v1'
# Pastilles nues (test, câblage, programmation) : sur la carte, hors nomenclature.
SANS_PIECE = ('Connector:TestPoint', 'Connector:Conn_ARM_SWD_TagConnect_TC2030')
SORTIE = bib.PROJET / 'kicad'
ESPACE = uuid.UUID('6f1c0f7e-2a7b-4c3e-9a51-6b8f1d2c4e90')
VERSION = '20250114'
REVISION = 'V1.0'
DATE = '2026-10-22'


def ident(*parties: str) -> str:
    return str(uuid.uuid5(ESPACE, '/'.join(parties)))


RACINE = ident('racine')
FEUILLE_UUID = {f: ident('feuille', f) for f in FEUILLES}


def symbole_uuid(ref: str) -> str:
    return ident('symbole', ref)


def chaine(texte: str) -> str:
    return '"' + texte.replace('\\', '\\\\').replace('"', '\\"') + '"'


def effets(taille=1.27, cache=False, justifier=None) -> str:
    j = f' (justify {justifier})' if justifier else ''
    h = ' (hide yes)' if cache else ''
    return f'(effects (font (size {taille} {taille})){j}{h})'


def definition_embarquee(identifiant: str) -> str:
    """Définition de bibliothèque recopiée dans le schéma, renommée « bibliothèque:nom »."""
    definition = bib.symbole(identifiant)
    nom = identifiant.split(':', 1)[1]
    return definition.replace(f'(symbol "{nom}"', f'(symbol "{identifiant}"', 1)


def unites(identifiant: str) -> list[int]:
    definition = bib.symbole(identifiant)
    nom = re.escape(identifiant.split(':', 1)[1])
    trouvees = {int(m.group(1)) for m in re.finditer(r'\(symbol "%s_(\d+)_\d+"' % nom, definition)}
    return sorted(u for u in trouvees if u > 0) or [1]


def boite(identifiant: str) -> tuple[float, float, float, float]:
    """Encombrement du symbole (repère du schéma, y vers le bas), broches et étiquettes comprises."""
    broches = bib.broches_symbole(identifiant).values()
    xs = [b['x'] for b in broches] or [0]
    ys = [-b['y'] for b in broches] or [0]
    marge = 14
    return min(xs) - marge, min(ys) - 4, max(xs) + marge, max(ys) + 4


def grille(v: float) -> float:
    return round(v / 1.27) * 1.27


def feuille_schema(feuille: str, page: int) -> str:
    composants = [c for c in COMPOSANTS if c.feuille == feuille]
    bibliotheques = sorted({c.symbole for c in composants} | ({'power:PWR_FLAG'} if feuille == 'alimentation' else set()))
    morceaux = [f'(kicad_sch (version {VERSION}) (generator "eeschema") (generator_version "9.0")',
                f'(uuid "{FEUILLE_UUID[feuille]}") (paper "A3")',
                f'(title_block (title {chaine(TITRES[feuille])}) (date "{DATE}") (rev "{REVISION}") '
                f'(company "Collectif Dedsec — FasoGuardian") (comment 1 "Bracelet FasoGuardian, carte V1 (FG-DOC-08 § 12)") '
                f'(comment 2 "Généré par materiel/carte-v1/conception/generer_schema.py depuis carte.py"))',
                '(lib_symbols ' + ' '.join(definition_embarquee(b) for b in bibliotheques) + ')']
    chemin = f'/{RACINE}/{FEUILLE_UUID[feuille]}'
    x0, y0, x, y, hauteur_ligne = 30.0, 40.0, 30.0, 40.0, 0.0
    largeur_page = 400.0
    for c, unite in [(c, u) for c in composants for u in unites(c.symbole)]:
        gauche, haut, droite, bas = boite(c.symbole)
        if x + (droite - gauche) > largeur_page:
            x, y = x0, y + hauteur_ligne + 10
            hauteur_ligne = 0
        sx, sy = grille(x - gauche), grille(y - haut)
        x += (droite - gauche) + 8
        hauteur_ligne = max(hauteur_ligne, bas - haut)
        toutes = bib.broches_symbole(c.symbole)
        broches = {n: b for n, b in toutes.items() if b['unite'] in (0, unite)}
        champs = [('Reference', c.ref, False), ('Value', c.valeur, False), ('Footprint', c.empreinte, True),
                  ('Datasheet', '', True), ('Fabricant', c.fabricant, True), ('LCSC', c.lcsc, True), ('Note', c.note, True)]
        # Une instance par unité (le double transistor en a deux) ; la première porte l'identifiant du composant.
        u = symbole_uuid(c.ref) if unite == 1 else ident('symbole', c.ref, str(unite))
        proprietes = ' '.join(
            f'(property "{n}" {chaine(v)} (at {sx} {sy + (-4 - 2 * i if i < 2 else 0)} 0) {effets(cache=cache)})'
            for i, (n, v, cache) in enumerate(champs))
        plots = ' '.join(f'(pin {chaine(n)} (uuid "{ident("broche", c.ref, n)}"))' for n in broches)
        morceaux.append(
            f'(symbol (lib_id {chaine(c.symbole)}) (at {sx} {sy} 0) (unit {unite}) (exclude_from_sim no) '
            f'(in_bom {"no" if c.symbole in SANS_PIECE else "yes"}) (on_board yes) (dnp {"yes" if c.non_monte else "no"}) (uuid "{u}") {proprietes} {plots} '
            f'(instances (project "{NOM}" (path "{chemin}" (reference "{c.ref}") (unit {unite})))))')
        for numero, broche in broches.items():
            px, py = grille(sx + broche['x']), grille(sy - broche['y'])
            if numero in c.broches:
                angle = {0: 180, 180: 0, 90: 270, 270: 90}[int(broche['angle']) % 360]
                justifier = 'right' if angle in (180, 270) else 'left'
                morceaux.append(
                    f'(global_label {chaine(c.broches[numero])} (shape passive) (at {px} {py} {angle}) (fields_autoplaced yes) '
                    f'{effets(justifier=justifier)} (uuid "{ident("etiquette", c.ref, numero)}") '
                    f'(property "Intersheetrefs" "${{INTERSHEET_REFS}}" (at {px} {py} 0) {effets(cache=True)}))')
            else:
                morceaux.append(f'(no_connect (at {px} {py}) (uuid "{ident("libre", c.ref, numero)}"))')
    if feuille == 'alimentation':
        # Drapeaux d'alimentation : les rails sont produits par des broches que l'ERC ne voit pas comme sources.
        for i, reseau in enumerate(ALIMENTATIONS):
            fx, fy = grille(40 + 22 * i), grille(250)
            morceaux.append(
                f'(symbol (lib_id "power:PWR_FLAG") (at {fx} {fy} 0) (unit 1) (exclude_from_sim no) (in_bom no) (on_board yes) '
                f'(dnp no) (uuid "{ident("drapeau", reseau)}") (property "Reference" "#FLG{i + 1:02d}" (at {fx} {fy - 5} 0) {effets(cache=True)}) '
                f'(property "Value" "PWR_FLAG" (at {fx} {fy - 3} 0) {effets(cache=True)}) (property "Footprint" "" (at {fx} {fy} 0) {effets(cache=True)}) '
                f'(property "Datasheet" "" (at {fx} {fy} 0) {effets(cache=True)}) (pin "1" (uuid "{ident("drapeau-broche", reseau)}")) '
                f'(instances (project "{NOM}" (path "{chemin}" (reference "#FLG{i + 1:02d}") (unit 1)))))')
            morceaux.append(
                f'(global_label {chaine(reseau)} (shape passive) (at {fx} {fy} 270) (fields_autoplaced yes) {effets(justifier="right")} '
                f'(uuid "{ident("drapeau-etiquette", reseau)}") (property "Intersheetrefs" "${{INTERSHEET_REFS}}" (at {fx} {fy} 0) {effets(cache=True)}))')
    morceaux.append('(embedded_fonts no))')
    return '\n'.join(morceaux)


def racine() -> str:
    morceaux = [f'(kicad_sch (version {VERSION}) (generator "eeschema") (generator_version "9.0") (uuid "{RACINE}") (paper "A3")',
                f'(title_block (title "Bracelet FasoGuardian — carte V1") (date "{DATE}") (rev "{REVISION}") '
                f'(company "Collectif Dedsec — FasoGuardian") (comment 1 "4 couches, ENIG, 40 x 34 mm, assemblage double face") '
                f'(comment 2 "Conception : FG-DOC-08 v3.0 ; décisions : materiel/carte-v1/CAHIER.md et ADR 0042"))',
                '(lib_symbols)']
    for i, feuille in enumerate(FEUILLES):
        x, y = 30 + (i % 4) * 90, 40 + (i // 4) * 60
        morceaux.append(
            f'(sheet (at {x} {y}) (size 70 40) (exclude_from_sim no) (in_bom yes) (on_board yes) (dnp no) '
            f'(fields_autoplaced yes) (stroke (width 0.1524) (type solid)) (fill (color 0 0 0 0.0000)) (uuid "{FEUILLE_UUID[feuille]}") '
            f'(property "Sheetname" {chaine(TITRES[feuille])} (at {x} {y - 1} 0) {effets(justifier="left bottom")}) '
            f'(property "Sheetfile" "{feuille}.kicad_sch" (at {x} {y + 41} 0) {effets(justifier="left top")}) '
            f'(instances (project "{NOM}" (path "/{RACINE}" (page "{i + 2}")))))')
    morceaux.append('(sheet_instances (path "/" (page "1")))')
    morceaux.append('(embedded_fonts no))')
    return '\n'.join(morceaux)


def main() -> None:
    SORTIE.mkdir(exist_ok=True)
    (SORTIE / f'{NOM}.kicad_sch').write_text(racine(), encoding='utf-8')
    for i, feuille in enumerate(FEUILLES):
        (SORTIE / f'{feuille}.kicad_sch').write_text(feuille_schema(feuille, i + 2), encoding='utf-8')
    (SORTIE / 'sym-lib-table').write_text(
        '(sym_lib_table (version 7)\n  (lib (name "fg")(type "KiCad")(uri "${KIPRJMOD}/../bibliotheque/fg.kicad_sym")'
        '(options "")(descr "Pièces LCSC contrôlées du projet FasoGuardian"))\n)\n', encoding='utf-8')
    (SORTIE / 'fp-lib-table').write_text(
        '(fp_lib_table (version 7)\n  (lib (name "fg")(type "KiCad")(uri "${KIPRJMOD}/../bibliotheque/fg.pretty")'
        '(options "")(descr "Empreintes LCSC contrôlées du projet FasoGuardian"))\n)\n', encoding='utf-8')
    projet = SORTIE / f'{NOM}.kicad_pro'
    if not projet.exists():
        projet.write_text(json.dumps({'meta': {'filename': f'{NOM}.kicad_pro', 'version': 3}, 'sheets': [
            [RACINE, 'Racine'], *[[FEUILLE_UUID[f], TITRES[f]] for f in FEUILLES]]}, indent=2), encoding='utf-8')
    print(f'schéma généré : {len(COMPOSANTS)} composants sur {len(FEUILLES)} feuilles dans {SORTIE}')


if __name__ == '__main__':
    main()
