"""Stock et coût des pièces chez JLCPCB pour chaque référence de la nomenclature d'assemblage, avant commande.

Usage : python outils/stock_jlc.py [nombre de cartes, 5 par défaut]
Pour chaque ligne de fabrication/nomenclature-jlcpcb.csv : référence LCSC, catégorie (base ou étendue), stock, coût
des pièces, et alerte si le stock ne couvre pas la commande. La quantité achetée est celle des cartes, plus la perte
machine annoncée par JLCPCB, et au moins son minimum d'achat ; le prix est celui de la tranche de quantité. Le total
ne compte que les pièces : le devis de JLCPCB ajoute la mise en route, les pochoirs, le chargement des pièces
étendues, la radiographie et le transport.
"""
import csv
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from jlc import chercher  # noqa: E402

NOMENCLATURE = Path(__file__).resolve().parent.parent / 'fabrication' / 'nomenclature-jlcpcb.csv'


def prix(piece: dict, quantite: int) -> float:
    """Prix unitaire de la tranche qui contient la quantité."""
    for tranche in sorted(piece.get('componentPrices') or [], key=lambda t: t['startNumber']):
        if quantite >= tranche['startNumber'] and (tranche['endNumber'] == -1 or quantite <= tranche['endNumber']):
            return tranche['productPrice']
    return 0.0


def main() -> int:
    cartes = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    manquants, etendues, total = 0, 0, 0.0
    with NOMENCLATURE.open(encoding='utf-8') as f:
        lignes = list(csv.DictReader(f))
    print(f'{"LCSC":>9}  {"référence":<26} {"qté":>4} {"achat":>6} {"stock":>8} {"coût $":>8}  catégorie')
    for ligne in lignes:
        code, quantite = ligne['LCSC Part #'], int(ligne['Quantity'])
        trouve = next((p for p in chercher(code, 10) if p.get('componentCode') == code), None)
        time.sleep(0.3)
        piece = trouve or {}
        achat = max(quantite * cartes + int(piece.get('lossNumber') or 0), int(piece.get('leastPatchNumber') or 0))
        stock = piece.get('stockCount', 0)
        categorie = piece.get('componentLibraryType', 'introuvable')
        cout = achat * prix(piece, achat)
        total += cout
        etendues += categorie == 'expand'
        alerte = '  <-- stock insuffisant' if stock < achat else ''
        manquants += stock < achat
        print(f'{code:>9}  {ligne["Manufacturer Part"][:26]:<26} {quantite:>4} {achat:>6} {stock:>8} {cout:>8.2f}'
              f'  {categorie}{alerte}')
    print(f'{len(lignes)} références dont {etendues} étendues, {manquants} en stock insuffisant pour {cartes} cartes ; '
          f'pièces : {total:.2f} $')
    return 1 if manquants else 0


if __name__ == '__main__':
    sys.exit(main())
