"""Recherche des références chez JLCPCB/LCSC : code LCSC, boîtier, stock et catégorie (base ou étendue).

Usage : python outils/jlc.py STM32U575CIT6 LIS2DW12TR ...
"""
import json
import sys
import urllib.request

URL = 'https://jlcpcb.com/api/overseas-pcb-order/v1/shoppingCart/smtGood/selectSmtComponentList'


def chercher(reference: str, taille: int = 6) -> list[dict]:
    corps = json.dumps({'keyword': reference, 'currentPage': 1, 'pageSize': taille}).encode()
    requete = urllib.request.Request(URL, data=corps, headers={'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(requete, timeout=30) as reponse:
        donnees = json.load(reponse)
    return ((donnees.get('data') or {}).get('componentPageInfo') or {}).get('list') or []


if __name__ == '__main__':
    for reference in sys.argv[1:]:
        print(f'== {reference}')
        for piece in chercher(reference):
            print(f"   {piece.get('componentCode'):>10}  {piece.get('componentModelEn', ''):<32} {piece.get('componentSpecificationEn', ''):<22}"
                  f" stock {piece.get('stockCount', 0):>7}  {piece.get('componentLibraryType')}")
