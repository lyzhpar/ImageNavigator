Plan futur pour navigation sans picker

Objectif

Eviter d'utiliser un picker à chaque lancement de NavigatorActivity. Lire directement les images du dossier sauvegardé dans l'aventure.

Problèmes à résoudre

Permission refusée : Android interdit d'accéder à un Uri si on n'a pas pris et sauvegardé la permission au moment du choix du dossier.

ACTION_OPEN_DOCUMENT_TREE obligatoire pour avoir l'accès à long terme.

Solution propre à mettre en place plus tard

Lors de l'import du dossier dans l'éditeur (EditorActivity)

Quand on utilise ACTION_OPEN_DOCUMENT_TREE, prendre la permission persistante avec :

contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

Sauvegarder le folderUri dans l'aventure (Adventure)

Ce sera déjà fait avec folderUri.toString().

Dans NavigatorActivity

Récupérer le folderUri depuis l'aventure.

Utiliser directement le Uri sans repasser par un picker.

Exception à prévoir

Si la permission n'est plus valide (ex: si l'utilisateur a déplacé ou supprimé le dossier), afficher un Toast :

"Accès au dossier perdu. Merci de réimporter le dossier."

Et ensuite proposer un picker seulement en secours.

Avantages de cette approche

Fluide pour l'utilisateur.

Zéro picker inutile.

Résilient aux changements (dossier déplacé, etc.).

Remarque

Le picker actuel est très bien pour la version simple !
On fera ça proprement plus tard quand tout fonctionnera bien.

Résumé rapide

🏋️ Toujours utiliser takePersistableUriPermission à l'import du dossier.

🔖 Stocker l'uri dans l'aventure.

🔑 Lire directement depuis cet uri dans NavigatorActivity.

🔦 Prévoir un plan B si ça plante.

✨ Courage, ton projet avance à grands pas !

