package com.stalker.player.data.model

import androidx.compose.runtime.mutableStateOf

object Strings {
    var lang = mutableStateOf("it")

    private fun tr(
        it: String,
        en: String,
        fr: String,
        de: String,
        es: String,
        ru: String
    ) = mapOf(
        "it" to it,
        "en" to en,
        "fr" to fr,
        "de" to de,
        "es" to es,
        "ru" to ru
    )

    private val strings = mapOf(
        "connect" to tr("Connetti", "Connect", "Connexion", "Verbinden", "Conectar", "Подключить"),
        "disconnect" to tr("Disconnetti", "Disconnect", "Déconnexion", "Trennen", "Desconectar", "Отключить"),
        "save" to tr("Salva", "Save", "Enregistrer", "Speichern", "Guardar", "Сохранить"),
        "cancel" to tr("Annulla", "Cancel", "Annuler", "Abbrechen", "Cancelar", "Отмена"),
        "close" to tr("Chiudi", "Close", "Fermer", "Schließen", "Cerrar", "Закрыть"),
        "play" to tr("Riproduci", "Play", "Lecture", "Wiedergabe", "Reproducir", "Воспроизвести"),
        "pause" to tr("Pausa", "Pause", "Pause", "Pause", "Pausa", "Пауза"),
        "loading" to tr("Caricamento...", "Loading...", "Chargement...", "Wird geladen...", "Cargando...", "Загрузка..."),
        "search" to tr("Cerca...", "Search...", "Rechercher...", "Suchen...", "Buscar...", "Поиск..."),
        "hostname" to tr("Indirizzo Host", "Hostname", "Adresse hôte", "Host-Adresse", "Dirección del host", "Адрес хоста"),
        "m3uSource" to tr("URL o file M3U", "M3U URL or file", "URL ou fichier M3U", "M3U-URL oder Datei", "URL o archivo M3U", "URL или файл M3U"),
        "chooseM3uFile" to tr("Scegli file M3U", "Choose M3U file", "Choisir un fichier M3U", "M3U-Datei auswählen", "Elegir archivo M3U", "Выбрать файл M3U"),
        "macAddress" to tr("Indirizzo MAC", "MAC Address", "Adresse MAC", "MAC-Adresse", "Dirección MAC", "MAC-адрес"),
        "username" to tr("Nome utente", "Username", "Nom d'utilisateur", "Benutzername", "Usuario", "Имя пользователя"),
        "password" to tr("Password", "Password", "Mot de passe", "Passwort", "Contraseña", "Пароль"),
        "portalType" to tr("Tipo Portale", "Portal Type", "Type de portail", "Portaltyp", "Tipo de portal", "Тип портала"),
        "savedProfiles" to tr("Profili Salvati", "Saved Profiles", "Profils enregistrés", "Gespeicherte Profile", "Perfiles guardados", "Сохраненные профили"),
        "saveProfile" to tr("Salva Profilo", "Save Profile", "Enregistrer le profil", "Profil speichern", "Guardar perfil", "Сохранить профиль"),
        "profiles" to tr("Profili", "Profiles", "Profils", "Profile", "Perfiles", "Профили"),
        "noProfiles" to tr("Nessun profilo", "No profiles", "Aucun profil", "Keine Profile", "Sin perfiles", "Нет профилей"),
        "name" to tr("Nome", "Name", "Nom", "Name", "Nombre", "Имя"),
        "type" to tr("Tipo", "Type", "Type", "Typ", "Tipo", "Тип"),
        "year" to tr("Anno", "Year", "Année", "Jahr", "Año", "Год"),
        "duration" to tr("Durata", "Duration", "Durée", "Dauer", "Duración", "Длительность"),
        "director" to tr("Regista", "Director", "Réalisateur", "Regisseur", "Director", "Режиссер"),
        "seasons" to tr("Stagioni", "Seasons", "Saisons", "Staffeln", "Temporadas", "Сезоны"),
        "episodes" to tr("Episodi", "Episodes", "Épisodes", "Folgen", "Episodios", "Серии"),
        "back" to tr("Indietro", "Back", "Retour", "Zurück", "Atrás", "Назад"),
        "account" to tr("Account", "Account", "Compte", "Konto", "Cuenta", "Аккаунт"),
        "server" to tr("Server", "Server", "Serveur", "Server", "Servidor", "Сервер"),
        "developer" to tr("Sviluppatore", "Developer", "Développeur", "Entwickler", "Desarrollador", "Разработчик"),
        "credits" to tr("Credits", "Credits", "Crédits", "Credits", "Créditos", "Авторы"),
        "expires" to tr("Scadenza", "Expires", "Expiration", "Ablauf", "Vencimiento", "Срок действия"),
        "settings" to tr("Impostazioni", "Settings", "Paramètres", "Einstellungen", "Ajustes", "Настройки"),
        "language" to tr("Lingua", "Language", "Langue", "Sprache", "Idioma", "Язык"),
        "nowPlaying" to tr("Ora in onda", "On now", "À l'antenne", "Läuft jetzt", "En emisión", "Сейчас в эфире"),
        "live" to tr("Diretta", "Live", "Direct", "Live", "En vivo", "Прямой эфир"),
        "movies" to tr("Film", "Movies", "Films", "Filme", "Películas", "Фильмы"),
        "series" to tr("Serie", "Series", "Séries", "Serien", "Series", "Сериалы"),
        "info" to tr("Info", "Info", "Infos", "Info", "Info", "Инфо"),
        "error" to tr("Errore", "Error", "Erreur", "Fehler", "Error", "Ошибка"),
        "tvApp" to tr("AstraTV", "AstraTV", "AstraTV", "AstraTV", "AstraTV", "AstraTV"),
        "maxOnline" to tr("Max Online", "Max Online", "Max en ligne", "Max online", "Máx. en línea", "Макс. онлайн"),
        "parental" to tr("Parental", "Parental", "Contrôle parental", "Jugendschutz", "Control parental", "Родительский контроль"),
        "stbMac" to tr("STB/MAC", "STB/MAC", "STB/MAC", "STB/MAC", "STB/MAC", "STB/MAC"),
        "xtream" to tr("Xtream", "Xtream", "Xtream", "Xtream", "Xtream", "Xtream"),
        "m3u" to tr("M3U", "M3U", "M3U", "M3U", "M3U", "M3U"),
        "seasonShort" to tr("S", "S", "S", "S", "T", "С"),
        "episodeShort" to tr("E", "E", "E", "E", "E", "Э"),
        "vodBadge" to tr("VOD", "VOD", "VOD", "VOD", "VOD", "VOD"),
        "seriesBadge" to tr("SERIE", "SERIES", "SÉRIES", "SERIEN", "SERIES", "СЕРИАЛЫ"),
        "allChannels" to tr("Tutti i canali", "All channels", "Toutes les chaînes", "Alle Kanäle", "Todos los canales", "Все каналы"),
        "m3uPlaylist" to tr("Playlist M3U", "M3U playlist", "Playlist M3U", "M3U-Playlist", "Lista M3U", "Плейлист M3U"),
        "genericError" to tr("Si è verificato un errore", "An error occurred", "Une erreur s'est produite", "Ein Fehler ist aufgetreten", "Se produjo un error", "Произошла ошибка"),
        "streamError" to tr("Errore di riproduzione", "Playback error", "Erreur de lecture", "Wiedergabefehler", "Error de reproducción", "Ошибка воспроизведения"),
        "openM3uError" to tr("Impossibile aprire il file M3U", "Cannot open the M3U file", "Impossible d'ouvrir le fichier M3U", "M3U-Datei kann nicht geöffnet werden", "No se puede abrir el archivo M3U", "Не удалось открыть файл M3U"),
        "loadingM3uError" to tr("Errore durante il caricamento della playlist M3U", "Error loading the M3U playlist", "Erreur lors du chargement de la playlist M3U", "Fehler beim Laden der M3U-Playlist", "Error al cargar la lista M3U", "Ошибка при загрузке плейлиста M3U"),
        "responseTooLarge" to tr("La risposta del server è troppo grande", "The server response is too large", "La réponse du serveur est trop volumineuse", "Die Serverantwort ist zu groß", "La respuesta del servidor es demasiado grande", "Ответ сервера слишком большой"),
        "emptyResponse" to tr("Risposta vuota dal server", "Empty response from the server", "Réponse vide du serveur", "Leere Antwort vom Server", "Respuesta vacía del servidor", "Пустой ответ от сервера"),
        "requestFailed" to tr("Richiesta al server non riuscita", "The server request failed", "La requête au serveur a échoué", "Serveranfrage fehlgeschlagen", "La solicitud al servidor falló", "Ошибка запроса к серверу"),
        "invalidResponse" to tr("Risposta server non valida", "Invalid server response", "Réponse serveur non valide", "Ungültige Serverantwort", "Respuesta del servidor no válida", "Неверный ответ сервера"),
        "missingToken" to tr("Token di accesso mancante", "Missing access token", "Jeton d'accès manquant", "Fehlendes Zugriffstoken", "Falta el token de acceso", "Отсутствует токен доступа"),
        "missingStreamUrl" to tr("URL stream mancante", "Missing stream URL", "URL du flux manquante", "Fehlende Stream-URL", "Falta la URL del stream", "Отсутствует URL потока"),
        "missingCommand" to tr("Comando stream mancante", "Missing stream command", "Commande du flux manquante", "Fehlender Stream-Befehl", "Falta el comando del stream", "Отсутствует команда потока"),
        "requestRetriesFailed" to tr("Tutti i tentativi di connessione sono falliti", "All connection attempts failed", "Toutes les tentatives de connexion ont échoué", "Alle Verbindungsversuche sind fehlgeschlagen", "Todos los intentos de conexión fallaron", "Все попытки подключения завершились неудачей"),
        "unknownItemType" to tr("Tipo elemento sconosciuto", "Unknown item type", "Type d'élément inconnu", "Unbekannter Elementtyp", "Tipo de elemento desconocido", "Неизвестный тип элемента"),
        "loadingCategories" to tr("Carico categorie...", "Loading categories...", "Chargement des catégories...", "Kategorien werden geladen...", "Cargando categorías...", "Загрузка категорий..."),
        "loadingLive" to tr("Carico canali...", "Loading channels...", "Chargement des chaînes...", "Sender werden geladen...", "Cargando canales...", "Загрузка каналов..."),
        "loadingVod" to tr("Carico film...", "Loading movies...", "Chargement des films...", "Filme werden geladen...", "Cargando películas...", "Загрузка фильмов..."),
        "loadingSeries" to tr("Carico serie...", "Loading series...", "Chargement des séries...", "Serien werden geladen...", "Cargando series...", "Загрузка сериалов..."),
        "loadingEpg" to tr("Carico EPG...", "Loading EPG...", "Chargement EPG...", "EPG wird geladen...", "Cargando EPG...", "Загрузка EPG..."),
        "loadingLogos" to tr("Carico loghi...", "Loading logos...", "Chargement des logos...", "Logos werden geladen...", "Cargando logos...", "Загрузка логотипов..."),
        "handshake" to tr("Connessione...", "Connecting...", "Connexion...", "Verbindung...", "Conectando...", "Подключение..."),
        "profile" to tr("Profilo...", "Profile...", "Profil...", "Profil...", "Perfil...", "Профиль..."),
        "accountInfo" to tr("Info account...", "Account info...", "Infos du compte...", "Kontoinfo...", "Info de la cuenta...", "Информация об аккаунте..."),
        "connecting" to tr("Connessione...", "Connecting...", "Connexion...", "Verbindung...", "Conectando...", "Подключение..."),
        "startingConnection" to tr("Avvio connessione...", "Starting connection...", "Démarrage de la connexion...", "Verbindung wird gestartet...", "Iniciando conexión...", "Запуск подключения..."),
        "pleaseWait" to tr("Attendere...", "Please wait...", "Veuillez patienter...", "Bitte warten...", "Espere...", "Пожалуйста, подождите..."),
        "exitPrompt" to tr("Premi di nuovo indietro per uscire", "Press back again to exit", "Appuyez encore sur retour pour quitter", "Zum Beenden erneut zurück drücken", "Pulsa atrás de nuevo para salir", "Нажмите назад еще раз для выхода"),
        "developedWith" to tr("Sviluppato con GPT5.4 e deepseek-v4-pro", "Developed with GPT5.4 and deepseek-v4-pro", "Développé avec GPT5.4 et deepseek-v4-pro", "Entwickelt mit GPT5.4 und deepseek-v4-pro", "Desarrollado con GPT5.4 y deepseek-v4-pro", "Разработано с GPT5.4 и deepseek-v4-pro"),
        "load" to tr("Carica", "Load", "Charger", "Laden", "Cargar", "Загрузить"),
        "delete" to tr("Elimina", "Delete", "Supprimer", "Löschen", "Eliminar", "Удалить"),
        "rewind10" to tr("Indietro 10s", "Rewind 10s", "Reculer 10 s", "10 Sek. zurück", "Retroceder 10 s", "Назад на 10 с"),
        "forward10" to tr("Avanti 10s", "Forward 10s", "Avancer 10 s", "10 Sek. vor", "Adelantar 10 s", "Вперед на 10 с"),
        "fullscreen" to tr("Schermo intero", "Fullscreen", "Plein écran", "Vollbild", "Pantalla completa", "Полный экран"),
        "exitFullscreen" to tr("Esci da schermo intero", "Exit fullscreen", "Quitter le plein écran", "Vollbild verlassen", "Salir de pantalla completa", "Выйти из полного экрана"),
        "notAvailable" to tr("N/D", "N/A", "N/D", "k. A.", "N/D", "Н/Д"),
        "noResults" to tr("Nessun risultato", "No results", "Aucun résultat", "Keine Ergebnisse", "Sin resultados", "Нет результатов"),
        "pleaseWaitLong" to tr("Attendere prego", "Please wait", "Veuillez patienter", "Bitte warten", "Espere por favor", "Пожалуйста, подождите"),
        "tvSubtitle" to tr("Android TV", "Android TV", "Android TV", "Android TV", "Android TV", "Android TV"),
        "addListsFromBrowser" to tr("Aggiungi liste da telefono o PC", "Add playlists from phone or PC", "Ajoutez des listes depuis téléphone ou PC", "Listen von Telefon oder PC hinzufügen", "Agrega listas desde teléfono o PC", "Добавьте списки с телефона или ПК"),
        "urlLabel" to tr("URL:", "URL:", "URL :", "URL:", "URL:", "URL:"),
        "codeLabel" to tr("CODICE:", "CODE:", "CODE :", "CODE:", "CÓDIGO:", "КОД:"),
        "serverStarting" to tr("Server in avvio...", "Server starting...", "Démarrage du serveur...", "Server wird gestartet...", "Servidor iniciándose...", "Сервер запускается..."),
        "loadingInProgress" to tr("Caricamento in corso...", "Loading in progress...", "Chargement en cours...", "Ladevorgang läuft...", "Carga en curso...", "Идет загрузка..."),
        "loginInProgress" to tr("Accesso in corso...", "Signing in...", "Connexion en cours...", "Anmeldung läuft...", "Acceso en curso...", "Выполняется вход..."),
        "plot" to tr("Trama", "Plot", "Synopsis", "Handlung", "Sinopsis", "Сюжет"),
        "noDescription" to tr("Nessuna descrizione disponibile.", "No description available.", "Aucune description disponible.", "Keine Beschreibung verfügbar.", "No hay descripción disponible.", "Описание недоступно."),
        "browseSeasons" to tr("Stagioni", "Seasons", "Saisons", "Staffeln", "Temporadas", "Сезоны"),
        "noEpisodesAvailable" to tr("Nessun episodio disponibile", "No episodes available", "Aucun épisode disponible", "Keine Folgen verfügbar", "No hay episodios disponibles", "Нет доступных серий"),
        "noSeasonsAvailable" to tr("Nessuna stagione disponibile", "No seasons available", "Aucune saison disponible", "Keine Staffeln verfügbar", "No hay temporadas disponibles", "Нет доступных сезонов"),
        "directedBy" to tr("Regia", "Directed by", "Réalisé par", "Regie", "Dirección", "Режиссер"),
        "cast" to tr("Cast", "Cast", "Distribution", "Besetzung", "Reparto", "В ролях"),
        "ageShort" to tr("Età", "Age", "Âge", "Alter", "Edad", "Возраст"),
        "connectionTimeout" to tr("Timeout di connessione: login annullato dopo 10 secondi", "Connection timeout: login cancelled after 10 seconds", "Délai de connexion dépassé : connexion annulée après 10 secondes", "Zeitüberschreitung bei der Verbindung: Anmeldung nach 10 Sekunden abgebrochen", "Tiempo de conexión agotado: inicio cancelado tras 10 segundos", "Тайм-аут соединения: вход отменен через 10 секунд"),
        "profileImported" to tr("Profilo '%s' importato da browser. Premi Connetti per caricarlo.", "Profile '%s' imported from browser. Press Connect to load it.", "Profil '%s' importé depuis le navigateur. Appuyez sur Connecter pour le charger.", "Profil '%s' aus dem Browser importiert. Drücke Verbinden, um es zu laden.", "Perfil '%s' importado desde el navegador. Pulsa Conectar para cargarlo.", "Профиль '%s' импортирован из браузера. Нажмите Подключить, чтобы загрузить его."),
        "profileDeleted" to tr("Profilo '%s' eliminato da browser.", "Profile '%s' deleted from browser.", "Profil '%s' supprimé depuis le navigateur.", "Profil '%s' im Browser gelöscht.", "Perfil '%s' eliminado desde el navegador.", "Профиль '%s' удален из браузера."),
        "stbSessionUnavailable" to tr("Sessione STB non disponibile", "STB session unavailable", "Session STB indisponible", "STB-Sitzung nicht verfügbar", "Sesión STB no disponible", "Сессия STB недоступна"),
        "confirmDeleteProfile" to tr("Vuoi davvero eliminare questo profilo?", "Do you really want to delete this profile?", "Voulez-vous vraiment supprimer ce profil ?", "Möchtest du dieses Profil wirklich löschen?", "¿De verdad quieres eliminar este perfil?", "Вы действительно хотите удалить этот профиль?"),
        "confirmDeleteProfileNamed" to tr("Eliminare il profilo '%s'?", "Delete profile '%s'?", "Supprimer le profil '%s' ?", "Profil '%s' löschen?", "¿Eliminar el perfil '%s'?", "Удалить профиль '%s'?"),
        "webImportTitle" to tr("Importa una lista o un profilo dal telefono/PC. Dopo il salvataggio, apri Profili sulla TV e carica il nuovo profilo.", "Import a playlist or profile from your phone/PC. After saving, open Profiles on the TV and load the new profile.", "Importez une liste ou un profil depuis votre téléphone/PC. Après l'enregistrement, ouvrez Profils sur la TV et chargez le nouveau profil.", "Importiere eine Liste oder ein Profil von Telefon/PC. Öffne nach dem Speichern Profile auf dem TV und lade das neue Profil.", "Importa una lista o perfil desde el teléfono/PC. Después de guardar, abre Perfiles en la TV y carga el nuevo perfil.", "Импортируйте плейлист или профиль с телефона/ПК. После сохранения откройте Профили на ТВ и загрузите новый профиль."),
        "newProfile" to tr("Nuovo profilo", "New profile", "Nouveau profil", "Neues Profil", "Nuevo perfil", "Новый профиль"),
        "profileName" to tr("Nome profilo", "Profile name", "Nom du profil", "Profilname", "Nombre del perfil", "Имя профиля"),
        "uploadFile" to tr("Oppure carica file M3U", "Or upload an M3U file", "Ou chargez un fichier M3U", "Oder M3U-Datei hochladen", "O sube un archivo M3U", "Или загрузите файл M3U"),
        "uploadLimit" to tr("Limite upload: 60 MB.", "Upload limit: 60 MB.", "Limite d'envoi : 60 Mo.", "Upload-Limit: 60 MB.", "Límite de carga: 60 MB.", "Лимит загрузки: 60 МБ."),
        "xtreamServer" to tr("Server Xtream", "Xtream server", "Serveur Xtream", "Xtream-Server", "Servidor Xtream", "Сервер Xtream"),
        "portalUrl" to tr("URL portale", "Portal URL", "URL du portail", "Portal-URL", "URL del portal", "URL портала"),
        "savedProfilesTitle" to tr("Profili salvati", "Saved profiles", "Profils enregistrés", "Gespeicherte Profile", "Perfiles guardados", "Сохраненные профили"),
        "saveOnTv" to tr("Salva su AstraTV", "Save to AstraTV", "Enregistrer sur AstraTV", "Auf AstraTV speichern", "Guardar en AstraTV", "Сохранить в AstraTV"),
        "enterTvCode" to tr("Inserisci il codice mostrato sulla TV.", "Enter the code shown on the TV.", "Entrez le code affiché sur la TV.", "Gib den auf dem TV angezeigten Code ein.", "Introduce el código mostrado en la TV.", "Введите код, показанный на ТВ."),
        "enter" to tr("Entra", "Enter", "Entrer", "Eintreten", "Entrar", "Войти"),
        "tvCodePlaceholder" to tr("Codice TV", "TV code", "Code TV", "TV-Code", "Código TV", "Код ТВ"),
        "enterUrlOrUpload" to tr("Inserisci un URL oppure carica un file M3U.", "Enter a URL or upload an M3U file.", "Saisissez une URL ou chargez un fichier M3U.", "Gib eine URL ein oder lade eine M3U-Datei hoch.", "Introduce una URL o sube un archivo M3U.", "Введите URL или загрузите файл M3U."),
        "xtreamCredsRequired" to tr("Username e password Xtream sono obbligatori.", "Xtream username and password are required.", "Le nom d'utilisateur et le mot de passe Xtream sont obligatoires.", "Xtream-Benutzername und Passwort sind erforderlich.", "El usuario y la contraseña de Xtream son obligatorios.", "Требуются имя пользователя и пароль Xtream."),
        "macRequired" to tr("MAC address obbligatorio per profilo MAC/STB.", "MAC address is required for MAC/STB profiles.", "L'adresse MAC est obligatoire pour un profil MAC/STB.", "Für MAC/STB-Profile ist eine MAC-Adresse erforderlich.", "La dirección MAC es obligatoria para perfiles MAC/STB.", "Для профиля MAC/STB требуется MAC-адрес."),
        "invalidProfile" to tr("Profilo non valido", "Invalid profile", "Profil non valide", "Ungültiges Profil", "Perfil no válido", "Недопустимый профиль"),
        "requestEmpty" to tr("Richiesta vuota", "Empty request", "Requête vide", "Leere Anfrage", "Solicitud vacía", "Пустой запрос"),
        "fileTooLarge" to tr("File troppo grande. Limite: %s MB", "File too large. Limit: %s MB", "Fichier trop volumineux. Limite : %s Mo", "Datei zu groß. Limit: %s MB", "Archivo demasiado grande. Límite: %s MB", "Файл слишком большой. Лимит: %s МБ"),
        "multipartInvalid" to tr("Multipart non valido", "Invalid multipart payload", "Multipart non valide", "Ungültige Multipart-Daten", "Multipart no válido", "Некорректный multipart"),
        "webImported" to tr("Profilo '%s' importato. Ora puoi caricarlo dalla TV in Profili.", "Profile '%s' imported. You can now load it from Profiles on the TV.", "Profil '%s' importé. Vous pouvez maintenant le charger depuis Profils sur la TV.", "Profil '%s' importiert. Du kannst es jetzt über Profile auf dem TV laden.", "Perfil '%s' importado. Ahora puedes cargarlo desde Perfiles en la TV.", "Профиль '%s' импортирован. Теперь вы можете загрузить его из Профилей на ТВ."),
        "webDeleted" to tr("Profilo '%s' eliminato.", "Profile '%s' deleted.", "Profil '%s' supprimé.", "Profil '%s' gelöscht.", "Perfil '%s' eliminado.", "Профиль '%s' удален.")
    )

    operator fun get(key: String): String = strings[key]?.get(lang.value) ?: strings[key]?.get("en") ?: key
    fun getFor(key: String, language: String): String = strings[key]?.get(language) ?: strings[key]?.get("en") ?: key

    fun portalLabel(type: String): String = when (type.lowercase()) {
        "mac", "stalker" -> this["stbMac"]
        "xtream" -> this["xtream"]
        "m3u" -> this["m3u"]
        else -> type
    }

    fun categoryTypeLabel(type: String): String = when (type) {
        "IPTV" -> this["live"]
        "VOD" -> this["movies"]
        "Series" -> this["series"]
        else -> type
    }

    fun episodeName(number: Int): String = when (lang.value) {
        "it" -> "Episodio $number"
        "fr" -> "Épisode $number"
        "de" -> "Folge $number"
        "es" -> "Episodio $number"
        "ru" -> "Серия $number"
        else -> "Episode $number"
    }

    fun seasonName(number: Int): String = when (lang.value) {
        "it" -> "Stagione $number"
        "fr" -> "Saison $number"
        "de" -> "Staffel $number"
        "es" -> "Temporada $number"
        "ru" -> "Сезон $number"
        else -> "Season $number"
    }

    fun localizeError(message: String): String {
        if (message.isBlank()) return this["genericError"]
        val lower = message.lowercase()
        return when {
            "cannot open m3u" in lower -> this["openM3uError"]
            "loading m3u" in lower -> this["loadingM3uError"]
            "response too large" in lower -> this["responseTooLarge"]
            "empty response" in lower -> this["emptyResponse"]
            "no token" in lower -> this["missingToken"]
            "no stream url" in lower -> this["missingStreamUrl"]
            "no command" in lower -> this["missingCommand"]
            "invalid " in lower -> this["invalidResponse"]
            "unknown item type" in lower -> this["unknownItemType"]
            "attempts failed" in lower -> this["requestRetriesFailed"]
            "timeout di connessione" in lower -> this["connectionTimeout"]
            "stb sessione non disponibile" in lower || "sessione stb non disponibile" in lower -> this["stbSessionUnavailable"]
            lower.startsWith("http ") -> this["requestFailed"]
            else -> message
        }
    }

    fun fmt(key: String, vararg args: Any): String = get(key).format(*args)
    fun fmtFor(key: String, language: String, vararg args: Any): String = getFor(key, language).format(*args)
}
