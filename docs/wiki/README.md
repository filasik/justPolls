# Wiki source

These pages are the source of the [GitHub wiki](https://github.com/filasik/justPolls/wiki). Page links use wiki syntax (`[Text](Page-Name)`), so they work on the wiki, not when browsing this folder.

To publish (after the first wiki page exists):

```bash
git clone https://github.com/filasik/justPolls.wiki.git
cp docs/wiki/*.md justPolls.wiki/ && rm justPolls.wiki/README.md
cd justPolls.wiki && git add -A && git commit -m "Update wiki" && git push
```
