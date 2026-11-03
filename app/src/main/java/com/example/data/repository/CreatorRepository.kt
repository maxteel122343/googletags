package com.example.data.repository

import com.example.data.model.CreatorVideo
import com.example.data.model.ImageSearchResult
import com.example.data.model.SearchTemplate
import com.example.data.model.VideoSearchResult
import com.example.data.model.WebSearchResult

object CreatorRepository {

    val defaultTemplates = listOf(
        SearchTemplate("t1", "", "Apenas @nickname", isSystem = true),
        SearchTemplate("t2", "fotos", "fotos (padrão)"),
        SearchTemplate("t3", "beach", "beach"),
        SearchTemplate("t4", "look", "look"),
        SearchTemplate("t5", "instagram", "instagram"),
        SearchTemplate("t6", "tiktok", "tiktok"),
        SearchTemplate("t7", "routine", "routine")
    )

    val sampleVideos = listOf(
        CreatorVideo(
            id = "v1",
            nickname = "ju.pasqualotto",
            displayName = "ju.pasqualotto",
            isVerified = true,
            caption = "sunny mornings & good coffee ☕ routine, caffè e caos creativo ✨",
            hashtags = listOf("#routine", "#milano", "#coffee", "#goodvibes"),
            audioTitle = "Giveon • Heartbreak Anniversary",
            likesCount = "1,234",
            commentsCount = "56",
            bookmarksCount = "201",
            sharesCount = "89",
            followerCount = "142K",
            bioSnippet = "Digital Creator & Lifestyle photographer based in Milan & São Paulo.",
            backgroundAsset = "image_87976832.png",
            avatarAsset = "image_25e5e779.png",
            totalResultsCount = "18,700",
            imageResults = listOf(
                ImageSearchResult(
                    id = "img1",
                    title = "Coastal mornings",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_87976832.png",
                    dateOrInfo = "Recent"
                ),
                ImageSearchResult(
                    id = "img2",
                    title = "Coffee & slow mornings",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_c0224455.png",
                    dateOrInfo = "2 days ago"
                ),
                ImageSearchResult(
                    id = "img3",
                    title = "Spring in the city",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_25e5e779.png",
                    dateOrInfo = "Last week"
                ),
                ImageSearchResult(
                    id = "img4",
                    title = "Weekend escapes",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_c77feb06.png",
                    dateOrInfo = "January 2024"
                ),
                ImageSearchResult(
                    id = "img5",
                    title = "Golden hour vibes",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_25e5e779.png",
                    dateOrInfo = "October 2023"
                )
            ),
            webResults = listOf(
                WebSearchResult(
                    id = "w1",
                    title = "@ju.pasqualotto – Instagram",
                    url = "https://www.google.com/search?q=ju.pasqualotto+instagram",
                    displayUrl = "instagram.com > ju.pasqualotto",
                    snippet = "Juliana Pasqualotto (@ju.pasqualotto) • Fotos e Reels no Instagram. Travel, lifestyle e momentos cotidianos.",
                    iconType = "instagram"
                ),
                WebSearchResult(
                    id = "w2",
                    title = "ju.pasqualotto – TikTok Profile",
                    url = "https://www.google.com/search?q=ju.pasqualotto+tiktok",
                    displayUrl = "tiktok.com > @ju.pasqualotto",
                    snippet = "Vídeos diários de rotina, bastidores de shoots e vlogs de viagem em Milão.",
                    iconType = "tiktok"
                ),
                WebSearchResult(
                    id = "w3",
                    title = "Juliana Pasqualotto – Portfolio & Blog",
                    url = "https://www.google.com/search?q=ju.pasqualotto+blog",
                    displayUrl = "jupasqualotto.com",
                    snippet = "Artigos sobre fotografia contemporânea, roteiros pela Itália e guias de cafés especiais.",
                    iconType = "blog"
                )
            ),
            topVideos = listOf(
                VideoSearchResult("tv1", "Morning coffee routine", "340K views", "image_87976832.png"),
                VideoSearchResult("tv2", "Milano fashion week backstage", "1.2M views", "image_c0224455.png"),
                VideoSearchResult("tv3", "Car talk & life updates", "510K views", "image_25e5e779.png")
            )
        ),
        CreatorVideo(
            id = "v2",
            nickname = "fitwithjoao",
            displayName = "João | Fitness Coach",
            isVerified = true,
            caption = "Discipline today, strength tomorrow. No shortcuts, just consistent reps. 💪",
            hashtags = listOf("#fitness", "#gym", "#workout", "#mindset"),
            audioTitle = "Original Sound – João",
            likesCount = "12.8K",
            commentsCount = "236",
            bookmarksCount = "1.7K",
            sharesCount = "512",
            followerCount = "18.6K",
            bioSnippet = "Fitness Coach | Helping you build strength, confidence and consistency. Instagram • 18.6K followers",
            backgroundAsset = "image_ca770d57.png",
            avatarAsset = "image_ca770d57.png",
            totalResultsCount = "24,300",
            imageResults = listOf(
                ImageSearchResult(
                    id = "img6",
                    title = "Shoulder & Chest routine",
                    sourceDomain = "fitwithjoao • Instagram",
                    assetFileName = "image_ca770d57.png",
                    dateOrInfo = "Workout Guide"
                ),
                ImageSearchResult(
                    id = "img7",
                    title = "Clean nutrition meal plan",
                    sourceDomain = "fitwithjoao • Instagram",
                    assetFileName = "image_ca770d57.png",
                    dateOrInfo = "Nutrition"
                ),
                ImageSearchResult(
                    id = "img8",
                    title = "Client transformation: 12 weeks",
                    sourceDomain = "fitwithjoao • Gymshark Athlete",
                    assetFileName = "image_ca770d57.png",
                    dateOrInfo = "Transformation"
                )
            ),
            webResults = listOf(
                WebSearchResult(
                    id = "w4",
                    title = "@fitwithjoao – Instagram",
                    url = "https://www.google.com/search?q=fitwithjoao+instagram",
                    displayUrl = "instagram.com > fitwithjoao",
                    snippet = "João | Fitness Coach. Online training programs & 1-on-1 personalized coaching.",
                    iconType = "instagram"
                ),
                WebSearchResult(
                    id = "w5",
                    title = "Fit With João – Training Hub",
                    url = "https://www.google.com/search?q=fitwithjoao+training",
                    displayUrl = "fitwithjoao.com",
                    snippet = "Customized workout plans, hypertrophy programming and strength conditioning programs.",
                    iconType = "blog"
                ),
                WebSearchResult(
                    id = "w6",
                    title = "Fit With João – YouTube Workouts",
                    url = "https://www.google.com/search?q=fitwithjoao+youtube",
                    displayUrl = "youtube.com > @fitwithjoao",
                    snippet = "Complete dumbbell workouts, form breakdown and high-protein recipe tutorials.",
                    iconType = "youtube"
                )
            ),
            topVideos = listOf(
                VideoSearchResult("tv4", "Top 3 exercises for bigger triceps", "890K views", "image_ca770d57.png"),
                VideoSearchResult("tv5", "Full push day workout breakdown", "1.4M views", "image_ca770d57.png")
            )
        ),
        CreatorVideo(
            id = "v3",
            nickname = "dancequeen_official",
            displayName = "dancequeen_official",
            isVerified = true,
            caption = "Feeling the beat ✨💃 #dance #fyp #trending choreo with friends!",
            hashtags = listOf("#dance", "#fyp", "#choreography", "#trending"),
            audioTitle = "Original Sound – dancequeen_official",
            likesCount = "12.8K",
            commentsCount = "256",
            bookmarksCount = "1,523",
            sharesCount = "512",
            followerCount = "920K",
            bioSnippet = "Dance Creator | Choreography | Good vibes ✨ Join me on TikTok and Instagram!",
            backgroundAsset = "image_fb30fbbb.png",
            avatarAsset = "image_fb30fbbb.png",
            totalResultsCount = "18,700",
            imageResults = listOf(
                ImageSearchResult(
                    id = "img9",
                    title = "Dance routine studio rehearsals",
                    sourceDomain = "dancequeen_official • TikTok",
                    assetFileName = "image_fb30fbbb.png",
                    dateOrInfo = "Studio Rehearsal"
                ),
                ImageSearchResult(
                    id = "img10",
                    title = "Viral street dance battle",
                    sourceDomain = "dancequeen_official • Instagram",
                    assetFileName = "image_fb30fbbb.png",
                    dateOrInfo = "Viral Clips"
                ),
                ImageSearchResult(
                    id = "img11",
                    title = "Grey sweatpants dance challenge",
                    sourceDomain = "dancequeen_official • TikTok",
                    assetFileName = "image_fb30fbbb.png",
                    dateOrInfo = "Challenge #1"
                )
            ),
            webResults = listOf(
                WebSearchResult(
                    id = "w7",
                    title = "dancequeen_official on TikTok",
                    url = "https://www.google.com/search?q=dancequeen_official+tiktok",
                    displayUrl = "tiktok.com/@dancequeen_official",
                    snippet = "Welcome to my TikTok! 💃 Dance videos, tutorials & behind the scenes. New videos every week!",
                    iconType = "tiktok"
                ),
                WebSearchResult(
                    id = "w8",
                    title = "Instagram @dancequeen_official",
                    url = "https://www.google.com/search?q=dancequeen_official+instagram",
                    displayUrl = "instagram.com/dancequeen_official",
                    snippet = "Choreography, dance tips & daily life ✨ Collabs and workshop schedules.",
                    iconType = "instagram"
                ),
                WebSearchResult(
                    id = "w9",
                    title = "Dance Queen Official YouTube Channel",
                    url = "https://www.google.com/search?q=dancequeen_official+youtube",
                    displayUrl = "youtube.com > @dancequeenofficial",
                    snippet = "Full 4K dance covers, tutorials in slow motion, and dancer podcasts.",
                    iconType = "youtube"
                )
            ),
            topVideos = listOf(
                VideoSearchResult("tv6", "Grey sweatpants dance choreo", "2.4M views", "image_fb30fbbb.png"),
                VideoSearchResult("tv7", "K-pop dance cover tutorial", "1.1M views", "image_fb30fbbb.png"),
                VideoSearchResult("tv8", "Freestyle session in studio", "780K views", "image_fb30fbbb.png")
            )
        ),
        CreatorVideo(
            id = "v4",
            nickname = "lifestyle.ana",
            displayName = "lifestyle.ana",
            isVerified = true,
            caption = "Sunset reset 🌅 Grateful for moments like these. Taking time to unplug and breathe.",
            hashtags = listOf("#sunset", "#mindfulness", "#lifestyle", "#travel"),
            audioTitle = "Beach House • Sunset Lover",
            likesCount = "1,234",
            commentsCount = "56",
            bookmarksCount = "78",
            sharesCount = "45",
            followerCount = "85K",
            bioSnippet = "Lifestyle. Travel. Wellness. Real moments, simple pleasures. 🌿",
            backgroundAsset = "image_78bd4e01.png",
            avatarAsset = "image_78bd4e01.png",
            totalResultsCount = "18,700",
            imageResults = listOf(
                ImageSearchResult(
                    id = "img12",
                    title = "Sunset reset on the beach",
                    sourceDomain = "lifestyle.ana • Instagram",
                    assetFileName = "image_78bd4e01.png",
                    dateOrInfo = "Sunset Series"
                ),
                ImageSearchResult(
                    id = "img13",
                    title = "Mindful morning habits & tea",
                    sourceDomain = "lifestyle.ana • Pinterest",
                    assetFileName = "image_87976832.png",
                    dateOrInfo = "Wellness"
                ),
                ImageSearchResult(
                    id = "img14",
                    title = "Coastal retreat cabin",
                    sourceDomain = "lifestyle.ana • Blog",
                    assetFileName = "image_78bd4e01.png",
                    dateOrInfo = "Cabin Vibes"
                )
            ),
            webResults = listOf(
                WebSearchResult(
                    id = "w10",
                    title = "@lifestyle.ana – Instagram",
                    url = "https://www.google.com/search?q=lifestyle.ana+instagram",
                    displayUrl = "instagram.com > lifestyle.ana",
                    snippet = "Lifestyle. Travel. Wellness. Real moments, simple pleasures. 🌿",
                    iconType = "instagram"
                ),
                WebSearchResult(
                    id = "w11",
                    title = "lifestyle.ana – Blog",
                    url = "https://www.google.com/search?q=lifestyle.ana+blog",
                    displayUrl = "lifestyleana.com",
                    snippet = "Thoughts on mindful living, slow travel & everyday inspiration.",
                    iconType = "blog"
                ),
                WebSearchResult(
                    id = "w12",
                    title = "lifestyle.ana – YouTube",
                    url = "https://www.google.com/search?q=lifestyle.ana+youtube",
                    displayUrl = "youtube.com > @lifestyle.ana",
                    snippet = "Vlogs, travel diaries & lifestyle tips. 🌸 Weekly Sunday uploads.",
                    iconType = "youtube"
                )
            ),
            topVideos = listOf(
                VideoSearchResult("tv9", "Sunset beach meditation", "420K views", "image_78bd4e01.png"),
                VideoSearchResult("tv10", "Slow living morning diary", "610K views", "image_87976832.png")
            )
        ),
        CreatorVideo(
            id = "v5",
            nickname = "ju.pasqualotto",
            displayName = "ju.pasqualotto",
            isVerified = true,
            caption = "Sun vibes ☀️ Car talks and good energy for the week ahead! São Paulo, Brasil 🇧🇷",
            hashtags = listOf("#sunvibes", "#brasil", "#saopaulo", "#lifestyle"),
            audioTitle = "Summer Acoustics • Tropicalia",
            likesCount = "1,248",
            commentsCount = "42",
            bookmarksCount = "130",
            sharesCount = "67",
            followerCount = "142K",
            bioSnippet = "Beach days, coffee and city views. Captured on iPhone & Leica.",
            backgroundAsset = "image_c77feb06.png",
            avatarAsset = "image_c77feb06.png",
            totalResultsCount = "15,200",
            imageResults = listOf(
                ImageSearchResult(
                    id = "img15",
                    title = "Beach days",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_c77feb06.png",
                    dateOrInfo = "January 2024"
                ),
                ImageSearchResult(
                    id = "img16",
                    title = "Night out",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_c0224455.png",
                    dateOrInfo = "November 2023"
                ),
                ImageSearchResult(
                    id = "img17",
                    title = "Coffee & good views",
                    sourceDomain = "ju.pasqualotto • Instagram",
                    assetFileName = "image_87976832.png",
                    dateOrInfo = "October 2023"
                )
            ),
            webResults = listOf(
                WebSearchResult(
                    id = "w13",
                    title = "@ju.pasqualotto – Instagram Profile",
                    url = "https://www.google.com/search?q=ju.pasqualotto+instagram",
                    displayUrl = "instagram.com > ju.pasqualotto",
                    snippet = "1.248 likes · ju.pasqualotto Sun vibes ☀️ View all 42 comments",
                    iconType = "instagram"
                ),
                WebSearchResult(
                    id = "w14",
                    title = "ju.pasqualotto Looks e Fotografia",
                    url = "https://www.google.com/search?q=ju.pasqualotto+looks",
                    displayUrl = "pinterest.com > jupasqualotto",
                    snippet = "Inspirações de looks casuais, maquiagem natural e fotografia em luz do dia.",
                    iconType = "blog"
                )
            ),
            topVideos = listOf(
                VideoSearchResult("tv11", "Sun vibes in SP", "950K views", "image_c77feb06.png"),
                VideoSearchResult("tv12", "Night out makeup GRWM", "1.3M views", "image_c0224455.png")
            )
        )
    )
}
