# Essais du noyau sur machine hôte. Le noyau ne dépend d'aucun matériel : il se compile avec n'importe quel
# compilateur C11. « make essais » construit et lance les essais ; toute alerte du compilateur est une erreur.
# Un comportement indéfini arrête l'essai sur place : pas de bibliothèque d'exécution, absente de musl.
CC ?= cc
CFLAGS ?= -std=c11 -O1 -g -Wall -Wextra -Wpedantic -Wshadow -Wconversion -Werror -fsanitize=undefined -fsanitize-undefined-trap-on-error
NOYAU := noyau/fg_codage.c noyau/fg_messages.c noyau/fg_commandes.c noyau/fg_ota.c noyau/fg_bracelet.c
SORTIE := build/essais

.PHONY: essais nettoyer
essais: $(SORTIE)
	./$(SORTIE)

$(SORTIE): $(NOYAU) tests/essais.c tests/port_hote.c $(wildcard noyau/*.h ports/*.h tests/*.h)
	mkdir -p build
	$(CC) $(CFLAGS) -o $@ $(NOYAU) tests/essais.c tests/port_hote.c

nettoyer:
	rm -rf build
