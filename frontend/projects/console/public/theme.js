// Applique le thème choisi avant le premier affichage, pour éviter un éclair de l'autre thème.
// Même règle que le service Theme : choix gardé sur l'appareil, sinon apparence du système.
(function () {
  try {
    var choix = localStorage.getItem('fg.theme');
    var clair = choix === 'clair' || (choix !== 'sombre' && window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches);
    if (clair) {
      document.documentElement.setAttribute('data-theme', 'clair');
    }
  } catch (erreur) {
    // Stockage indisponible : le thème sombre par défaut s'applique.
  }
})();
