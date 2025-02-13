## TODO LIST
- [ ] Optimize prints
- [ ] Optimize logs 
- [ ] Optimize compatibility with Telnet and ClientChat
- [ ] ClientChat beacon "[>]" print for user
- [ ] Implement /sendFileTo command (only for ClientChat)


commande : /sendFileTo <filename> <username>
Le client à un parser qui voit que l'utilisateur à fait un /sendFileTo (on est dans le main et on recupère le inputConsole)
On a désormais une phase préalable qui regarde s'il y a un sendFile
pour cela on fait un traitement avant de l'envoyer au serveur. (fonction qui transformera notre message)

intput : /sendFileTo filename pseudo
client (il transforme notre message en) : /msgTo pseudo FILE:base64(filename):base64(file) -> Exemple : FILE:dG90by50eHQ=:bXlfc3VwZXJfZmlsZV9oYXNfYmVlbl9zZW50
-> serveur
-> pseudo : FILE:base64(namefile):base64(file)
-> pseudo : decode fichier

input : /sendFileC filename pseudo
->
-> serveur
-> pseudo : FILE:base64(namefile):base64(file)