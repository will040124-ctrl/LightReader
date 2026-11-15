// HTML parsing example using public xkcd pages. Search by comic number, e.g. 1.
class XkcdHtmlExample extends ComicSource {
    key = "xkcd_html_example";
    name = "xkcd（网页解析示例）";
    version = "1.0.0";
    lightreaderApiVersion = 2;
    domains = ["xkcd.com", "imgs.xkcd.com"];
    settings = { author: { default: "Randall Munroe" } };

    async info(id) {
        if (!/^[1-9][0-9]{0,5}$/.test(String(id))) throw Error("此示例请按漫画编号搜索，例如 1 或 614");
        const response = await Network.get(`https://xkcd.com/${id}/`);
        if (response.status !== 200) throw Error(`网站返回 HTTP ${response.status}`);
        const document = new HtmlDocument(response.body);
        try {
            const title = document.getElementById("ctitle")?.text.trim();
            const image = document.querySelector("#comic > img")?.attributes;
            if (!title || !image?.src) throw Error("网页没有返回漫画信息");
            const url = image.src.startsWith("//") ? "https:" + image.src : image.src;
            return { title, image: url, description: image.title || image.alt || "" };
        } finally { document.dispose(); }
    }
    search = {
        load: async (keyword) => {
            const info = await this.info(keyword.trim());
            return { comics: [new Comic({ id: keyword.trim(), title: info.title,
                subtitle: this.loadSetting("author"), cover: info.image })], maxPage: 1 };
        }
    };
    comic = {
        loadInfo: async (id) => {
            const info = await this.info(id);
            return new ComicDetails({ title: info.title, subtitle: this.loadSetting("author"),
                cover: info.image, description: info.description, chapters: null });
        },
        loadEp: async (id) => ({ images: [(await this.info(id)).image] }),
        onImageLoad: () => ({ headers: { Referer: "https://xkcd.com/" } })
    };
}
