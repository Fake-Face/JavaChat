## TODO LIST
- [ ] Optimize prints
- [ ] Optimize logs 
- [ ] Optimize compatibility with Telnet and ClientChat
- [ ] ClientChat beacon "[>]" print for user
- [ ] Implement /sendFileTo command (only for ClientChat)


commande : /sendFileTo <filename> <username>
Le client à un parser qui voit que l'utilisateur à fait un /sendFileTo (on est dans le main et on recupère le inputConsole)
On a désormais une phase préalable qui regarde s'il y a un sendFile (if ou switch case)
pour cela on fait un traitement avant de l'envoyer au serveur. (fonction qui transformera notre message)

intput utilisateur : /sendFileTo filename pseudo
client, il transforme notre message en : /msgTo pseudo FILE:base64(filename):base64(file) -> Exemple : FILE:dG90by50eHQ=:bXlfc3VwZXJfZmlsZV9oYXNfYmVlbl9zZW50
-> envoi au serveur le fichier 
-> Le serveur stocke temporairement le fichier
-> le serveur envoi un message au destinataire pour lui dire qu'il a un fichier à recevoir
-> le destinataire accepte ou refuse le fichier
-> (si oui) le serveur l'envoi au destinataire et s'affiche de la forme suivante : msg from <username> : FILE:base64(namefile):base64(file) + print un message au destinataire que le fichier à bien été téléchargé puis envoi à l'envoyeur un message disant que le fichier a été envoyé avec succès
-> (si non) le serveur supprime le fichier temporaire et renvoi un message à l'initiateur du fichier que le fichier a été refusé

-> pseudo : decode fichier

input : /sendFileC filename pseudo
->
-> serveur
-> pseudo : FILE:base64(namefile):base64(file)