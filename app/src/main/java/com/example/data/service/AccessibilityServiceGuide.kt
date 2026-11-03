package com.example.data.service

/**
 * GUIA TÉCNICO DE IMPLEMENTAÇÃO EM ANDROID REAL:
 * ===============================================
 * Como implementar a detecção automática de criadores sobre aplicativos de terceiros
 * (TikTok, Instagram Reels e YouTube Shorts) utilizando AccessibilityService + WindowManager Overlay.
 *
 * 1. O QUE É E POR QUE USAR ACCESSIBILITY SERVICE?
 *    Em sistemas Android, aplicativos regulares são executados em sandbox isolados e não podem
 *    ler diretamente a tela de outros apps sem permissão especial. O Android AccessibilityService
 *    permite inspecionar a hierarquia de nós da interface do usuário (AccessibilityNodeInfo)
 *    em tempo real quando o usuário está usando outros aplicativos.
 *
 * 2. CONFIGURAÇÃO NO ANDROIDMANIFEST.XML:
 *    ```xml
 *    <service
 *        android:name=".service.CreatorDetectorAccessibilityService"
 *        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
 *        android:exported="true">
 *        <intent-filter>
 *            <action android:name="android.accessibilityservice.AccessibilityService" />
 *        </intent-filter>
 *        <meta-data
 *            android:name="android.accessibilityservice"
 *            android:resource="@xml/accessibility_service_config" />
 *    </service>
 *
 *    <!-- Permissão para desenhar o Card Flutuante / Split Screen sobre outros apps -->
 *    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
 *    ```
 *
 * 3. ARQUIVO RES/XML/ACCESSIBILITY_SERVICE_CONFIG.XML:
 *    ```xml
 *    <accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
 *        android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
 *        android:accessibilityFeedbackType="feedbackGeneric"
 *        android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds"
 *        android:canRetrieveWindowContent="true"
 *        android:description="@string/accessibility_service_description"
 *        android:notificationTimeout="100"
 *        android:packageNames="com.zhiliaoapp.musically,com.ss.android.ugc.trill,com.instagram.android,com.google.android.youtube" />
 *    ```
 *
 * 4. PACOTES DOS APLICATIVOS SUPORTADOS:
 *    - TikTok Global: "com.zhiliaoapp.musically"
 *    - TikTok Ásia / Alternativo: "com.ss.android.ugc.trill"
 *    - Instagram: "com.instagram.android"
 *    - YouTube Shorts: "com.google.android.youtube"
 *
 * 5. LÓGICA DE EXTRAÇÃO DO NICKNAME NO CÓDIGO KOTLIN:
 *    Ao receber onAccessibilityEvent(event: AccessibilityEvent):
 *    - Verificar event.packageName
 *    - Percorrer event.source (AccessibilityNodeInfo) recursivamente ou buscar por View IDs:
 *      * TikTok:
 *        nó com View ID `com.zhiliaoapp.musically:id/author_name` ou `com.zhiliaoapp.musically:id/title`
 *        ou nó com texto iniciando com `@`
 *      * Instagram:
 *        nó com View ID `com.instagram.android:id/row_feed_photo_profile_name`
 *        ou nós em tela de Reels com formato `username`
 *      * YouTube Shorts:
 *        nó com View ID `com.google.android.youtube:id/channel_name` ou prefixado por `@`
 *
 * 6. EXIBIÇÃO DO OVERLAY FLUTUANTE (WINDOW MANAGER):
 *    Utiliza-se `windowManager.addView(composeView, layoutParams)` com:
 *    `layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
 *    permitindo exibir qualquer uma das 5 interfaces (Card Flutuante, Barra Inferior, etc.)
 *    diretamente flutuando por cima do TikTok ou Instagram!
 */

object AccessibilityGuideData {
    val supportedApps = listOf(
        AppPackageInfo(
            name = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            altPackage = "com.ss.android.ugc.trill",
            idPattern = "com.zhiliaoapp.musically:id/author_name",
            description = "Extrai o nickname do autor a cada scroll vertical no For You feed."
        ),
        AppPackageInfo(
            name = "Instagram Reels",
            packageName = "com.instagram.android",
            altPackage = null,
            idPattern = "com.instagram.android:id/row_feed_photo_profile_name",
            description = "Detecta o @ do perfil em Reels e publicações do feed principal."
        ),
        AppPackageInfo(
            name = "YouTube Shorts",
            packageName = "com.google.android.youtube",
            altPackage = null,
            idPattern = "com.google.android.youtube:id/channel_name",
            description = "Lê o handle do canal (@canal) ao trocar de vídeo curto."
        )
    )

    const val CODE_SAMPLE_SERVICE = """
// Exemplo real de implementação do AccessibilityService:
class CreatorDetectorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val currentPackage = event?.packageName?.toString() ?: return

        val detectedNickname = when (currentPackage) {
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> extractTikTokCreator(rootNode)
            "com.instagram.android" -> extractInstagramCreator(rootNode)
            "com.google.android.youtube" -> extractYouTubeShortsCreator(rootNode)
            else -> null
        }

        if (!detectedNickname.isNullOrBlank()) {
            OverlayManager.updateSearchOverlay(detectedNickname)
        }
    }

    private fun extractTikTokCreator(root: AccessibilityNodeInfo): String? {
        // Busca direta pelo View ID do nickname do autor
        val authorNodes = root.findAccessibilityNodeInfosByViewId(
            "com.zhiliaoapp.musically:id/author_name"
        )
        if (authorNodes.isNotEmpty()) {
            val raw = authorNodes[0].text?.toString() ?: return null
            return raw.removePrefix("@").trim()
        }
        // Fallback recursivo buscando por padrão de texto @
        return findNodeWithRegex(root, Regex("^@[a-zA-Z0-9._]+$"))
    }
}
"""
}

data class AppPackageInfo(
    val name: String,
    val packageName: String,
    val altPackage: String?,
    val idPattern: String,
    val description: String
)
