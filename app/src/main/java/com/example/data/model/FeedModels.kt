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
        subtitle = "Lateral Direita (Split Screen)",
        description = "Vídeo na esquerda e coluna com cards verticais de imagens do Google na direita.",
        iconName = "vertical_split"
    ),
    COMPACT_FLOATING_CARD(
        title = "3. Card Flutuante",
        subtitle = "Popup Compacto (Overlay)",
        description = "Pequeno card flutuante com dados do criador + Google e botão 'See more results'.",
        iconName = "picture_in_picture"
    ),
    FULL_SPLIT_SCREEN(
        title = "4. Tela Dividida 50/50",
        subtitle = "Google Search Completo",
        description = "Metade vídeo + metade Google Search escuro completo (All, Videos, Images, etc.).",
        iconName = "splitscreen"
    ),
    BOTTOM_SHEET(
        title = "5. Bottom Sheet",
        subtitle = "Painel Deslizante",
        description = "Painel expansível que sobe da base com resultados do Google (Instagram, YouTube, Blog).",
        iconName = "expand_less"
    ),
    EXTRACTED_IMAGES_SCROLL(
        title = "6. Fotos Extraídas",
        subtitle = "Horizontal Scroll (V6)",
        description = "Painel inferior estilo V5 com extração JS das fotos do Google em rolagem horizontal limpa.",
        iconName = "collections"
    ),
    CAROUSEL_PHOTO_CARDS(
        title = "7. Carrossel de Fotos",
        subtitle = "Cards Horizontais (V7)",
        description = "Cards elegantes em carrossel horizontal com fotos e dados extraídos do Google.",
        iconName = "view_carousel"
    );

    val id: Int get() = ordinal + 1
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
