package com.example.data.model

enum class InterfaceVersion(
    val title: String,
    val subtitle: String,
    val description: String,
    val iconName: String
) {
    BOTTOM_HORIZONTAL_CARDS(
        title = "1. Cards Horizontais",
        subtitle = "Estilo Reels (Inferior)",
        description = "Vídeo ocupa a maior parte da tela com barra inferior deslizável de fotos do Google Imagens.",
        iconName = "view_agenda"
    ),
    SIDE_VERTICAL_CARDS(
        title = "2. Cards Verticais",
        subtitle = "Lateral Direita (Sidebar)",
        description = "Vídeo na esquerda e barra lateral direita com cards verticais de imagens do Google.",
        iconName = "vertical_split"
    ),
    BOTTOM_SHEET(
        title = "5. Bottom Sheet",
        subtitle = "Painel Deslizante",
        description = "Painel expansível que sobe da base com resultados do Google (redimensionável na borda superior).",
        iconName = "expand_less"
    ),
    EXTRACTED_IMAGES_SCROLL(
        title = "6. Fotos na Vertical",
        subtitle = "Vertical Limpo (V6)",
        description = "Apenas as fotos do criador na vertical (uma abaixo da outra), sem página do Google e sem bordas.",
        iconName = "view_stream"
    ),
    CAROUSEL_PHOTO_CARDS(
        title = "7. Fotos na Horizontal",
        subtitle = "Carrossel Limpo (V7)",
        description = "Apenas as fotos do criador na horizontal (deslize para o lado para ver a próxima), estilo carrossel grande e limpo.",
        iconName = "view_carousel"
    );

    val id: Int get() = when (this) {
        BOTTOM_HORIZONTAL_CARDS -> 1
        SIDE_VERTICAL_CARDS -> 2
        BOTTOM_SHEET -> 5
        EXTRACTED_IMAGES_SCROLL -> 6
        CAROUSEL_PHOTO_CARDS -> 7
    }
}

enum class SearchEngineType(val displayName: String, val badge: String) {
    GOOGLE_IMAGES("Google Imagens", "Imagens"),
    GOOGLE_SEARCH("Google Search Completo", "Web & All")
}

data class SearchTemplate(
    val id: String,
    val keyword: String,
    val label: String,
    val isSystem: Boolean = false
)

data class ImageSearchResult(
    val id: String,
    val title: String,
    val sourceDomain: String,
    val assetFileName: String? = null,
    val webImageUrl: String? = null,
    val dateOrInfo: String = "2024"
)

data class WebSearchResult(
    val id: String,
    val title: String,
    val url: String,
    val displayUrl: String,
    val snippet: String,
    val iconType: String // "instagram", "youtube", "blog", "tiktok"
)

data class VideoSearchResult(
    val id: String,
    val title: String,
    val views: String,
    val assetFileName: String? = null
)

data class CreatorVideo(
    val id: String,
    val nickname: String,          // e.g. "ju.pasqualotto"
    val displayName: String,       // e.g. "Juliana Pasqualotto"
    val isVerified: Boolean = true,
    val caption: String,
    val hashtags: List<String> = emptyList(),
    val audioTitle: String,
    val likesCount: String,
    val commentsCount: String,
    val bookmarksCount: String,
    val sharesCount: String,
    val followerCount: String,
    val bioSnippet: String,
    val backgroundAsset: String,   // image asset path for video frame
    val avatarAsset: String,       // avatar image asset
    val imageResults: List<ImageSearchResult>,
    val webResults: List<WebSearchResult>,
    val topVideos: List<VideoSearchResult> = emptyList(),
    val totalResultsCount: String = "18,700"
)
