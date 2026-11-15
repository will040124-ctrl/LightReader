// LightReader basic comic API example, using https://xkcd.com/json.html .
// Search by comic number, e.g. 1 or 614. This is not a general keyword search source.
class XkcdExample extends ComicSource {
    key = "xkcd_example";
    name = "xkcd（编号检索示例）";
    version = "1.0.0";
    lightreaderApiVersion = 1;
    domains = ["xkcd.com", "imgs.xkcd.com"];

    async info(id) {
        if (!/^[1-9][0-9]{0,5}$/.test(String(id))) throw Error("此示例请按漫画编号搜索，例如 1 或 614");
        const response = await Network.get(`https://xkcd.com/${id}/info.0.json`);
        if (response.status !== 200) throw Error(`未找到此编号，网站返回 HTTP ${response.status}`);
        return JSON.parse(response.body);
    }

    search = {
        load: async (keyword, page, options) => {
            const info = await this.info(keyword.trim());
            return { comics: [new Comic({ id: String(info.num), title: info.safe_title || info.title,
                subtitle: "Randall Munroe", cover: info.img })], maxPage: 1 };
        }
    };

    comic = {
        loadInfo: async (id) => {
            const info = await this.info(id);
            return new ComicDetails({ title: info.safe_title || info.title, subtitle: "Randall Munroe",
                cover: info.img, description: info.alt, chapters: null });
        },
        loadEp: async (id, episode) => ({ images: [(await this.info(id)).img] }),
        onImageLoad: () => ({ headers: { Referer: "https://xkcd.com/" } })
    };
}
