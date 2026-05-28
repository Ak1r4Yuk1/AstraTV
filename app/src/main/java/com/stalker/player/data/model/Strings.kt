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
        "notAvailable" to tr("N/D", "N/A", "N/D", "k. A.", "N/D", "Н/Д")
    )

    operator fun get(key: String): String = strings[key]?.get(lang.value) ?: strings[key]?.get("en") ?: key

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
            lower.startsWith("http ") -> this["requestFailed"]
            else -> message
        }
    }
}
