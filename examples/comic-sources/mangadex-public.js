// LightReader adapter for MangaDex's public API, independently implemented here.
// No account, forum, payment, or externally hosted chapter support.
class MangaDexPublic extends ComicSource {
    key = "mangadex_public";
    name = "MangaDex · 公开漫画";
    version = "1.0.1";
    lightreaderApiVersion = 2;
    domains = ["api.mangadex.org", "uploads.mangadex.org", "*.mangadex.network"];
    settings = {
        language: { title: "目录翻译语言", type: "select", default: "en", options: [
            { value: "all", text: "全部可用语言" },
            { value: "en", text: "英语" }, { value: "zh", text: "简体中文" },
            { value: "zh-hk", text: "繁体中文" }, { value: "ja", text: "日语" }
        ] },
        quality: { title: "漫画画质", type: "select", default: "saver", options: [
            { value: "saver", text: "节省流量" }, { value: "original", text: "原图" }
        ] }
    };
    async json(path) {
        const response = await Network.get("https://api.mangadex.org" + path);
        if (response.status === 429) throw Error("MangaDex 请求过于频繁，请稍后重试");
        if (response.status !== 200) throw Error("MangaDex 返回 HTTP " + response.status);
        const data = JSON.parse(response.body);
        if (data.result !== "ok") throw Error("MangaDex 未返回有效结果");
        return data;
    }
    checkId(id) {
        if (!/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(id)) throw Error("漫画或章节标识无效");
    }
    languageName(code) {
        return ({en:"英语",zh:"简体中文","zh-hk":"繁体中文",ja:"日语",cs:"捷克语",sr:"塞尔维亚语",es:"西班牙语","es-la":"拉美西班牙语",ru:"俄语",uk:"乌克兰语",vi:"越南语","pt-br":"巴西葡萄牙语"})[code] || code;
    }
    readable(item) { return !item.attributes.isUnavailable && item.attributes.pages > 0 && !item.attributes.externalUrl; }
    brief(item) {
        const a = item.attributes, titles = { ...a.title };
        for (const alt of a.altTitles || []) for (const [lang, title] of Object.entries(alt)) if (!titles[lang]) titles[lang] = title;
        const cover = item.relationships.find(r => r.type === "cover_art")?.attributes?.fileName;
        return { id: item.id, title: titles.zh || titles["zh-hk"] || titles.en || Object.values(titles)[0],
            subtitle: item.relationships.filter(r => r.type === "author").map(r => r.attributes?.name || "").filter(Boolean).join(" / "),
            cover: cover ? `https://uploads.mangadex.org/covers/${item.id}/${encodeURIComponent(cover)}.256.jpg` : "" };
    }
    search = { load: async (keyword, options, page) => {
        const offset = (page - 1) * 20;
        const result = await this.json(`/manga?limit=20&offset=${offset}&title=${encodeURIComponent(keyword)}&includes[]=cover_art&includes[]=author&contentRating[]=safe&hasAvailableChapters=true`);
        return { comics: result.data.map(item => this.brief(item)), maxPage: Math.ceil(result.total / 20) };
    } };
    comic = {
        loadInfo: async id => {
            this.checkId(id);
            const manga = (await this.json(`/manga/${id}?includes[]=cover_art&includes[]=author`)).data;
            const chapters = new Map(), language = this.loadSetting("language");
            let offset = 0, total = 1;
            while (offset < total) {
                if (offset >= 10000) throw Error("目录超过本次支持的 10000 项，未保存不完整目录");
                const filter=language==="all"?"":`&translatedLanguage[]=${encodeURIComponent(language)}`;
                const result = await this.json(`/manga/${id}/feed?limit=500&offset=${offset}${filter}&order[volume]=asc&order[chapter]=asc&contentRating[]=safe`);
                total = result.total;
                if (!result.data.length && offset < total) throw Error("目录分页不完整，请稍后重试");
                for (const item of result.data) {
                    const a = item.attributes;
                    if (!this.readable(item)) continue;
                    const volume = (a.volume ? "卷 " + a.volume : "未分卷")+(language==="all"?" · "+this.languageName(a.translatedLanguage):"");
                    if (!chapters.has(volume)) chapters.set(volume, new Map());
                    const number = a.chapter ? "第 " + a.chapter + " 话" : "短篇";
                    const title = number + (a.title ? " · " + a.title : "");
                    chapters.get(volume).set(item.id, title);
                }
                offset += result.data.length;
            }
            if (!chapters.size) {
                const available=language==="all"?[]:(await this.json(`/manga/${id}/feed?limit=500&contentRating[]=safe`)).data.filter(item=>this.readable(item));
                const languages=[...new Set(available.map(item=>item.attributes.translatedLanguage))].map(code=>this.languageName(code));
                const reason=languages.length?`当前${this.languageName(language)}没有可直接阅读的章节，可能未收录或已被站点标为不可用。可用目录包含：${languages.join("、")}。请到源设置选择“全部可用语言”或切换对应语言。`:
                    "当前选择下没有可直接阅读的站内章节，可能未收录、不可用或仅有外链。可尝试在源设置选择“全部可用语言”；作品信息存在不代表章节仍可阅读。";
                throw Error("LR_CONTENT_UNAVAILABLE:"+reason);
            }
            const descriptions = manga.attributes.description || {};
            return new ComicDetails({ ...this.brief(manga), description: descriptions.zh || descriptions["zh-hk"] || descriptions.en || Object.values(descriptions)[0] || "",
                status: manga.attributes.status, chapters });
        },
        loadEp: async (id, episode) => {
            this.checkId(id);this.checkId(episode);
            const result = await this.json(`/at-home/server/${episode}?forcePort443=true`);
            // The wildcard is shown for explicit approval during source import.
            if (result.baseUrl !== "https://uploads.mangadex.org" && !/^https:\/\/[a-z0-9-]+\.mangadex\.network$/.test(result.baseUrl))
                throw Error("图片线路已变化：" + result.baseUrl + "；请核对源实现和访问授权");
            const chapter = result.chapter;
            const saver = this.loadSetting("quality") === "saver" && chapter.dataSaver?.length;
            const files = saver ? chapter.dataSaver : chapter.data;
            if (!Array.isArray(files) || !files.length) throw Error("此话没有可用图片");
            return { images: files.map(file => `${result.baseUrl}/${saver ? "data-saver" : "data"}/${encodeURIComponent(chapter.hash)}/${encodeURIComponent(file)}`) };
        }
    };
}
