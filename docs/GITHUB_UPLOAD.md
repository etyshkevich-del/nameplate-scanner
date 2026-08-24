# Как загрузить проект в GitHub

Проект уже инициализирован как Git-репозиторий с веткой `main`. Модель, APK,
локальный Android SDK и результаты сборки исключены через `.gitignore`.

## 1. Проверьте автора коммитов

```bash
git config user.name "Ваше имя"
git config user.email "email@example.com"
```

## 2. Создайте первый коммит

```bash
git add .
git commit -m "Initial release: offline Excel and multi-document RAG"
```

## 3. Создайте пустой репозиторий на GitHub

Не добавляйте через сайт README, `.gitignore` или лицензию: эти файлы уже есть
локально. Для исходного кода без согласованной лицензии безопаснее сначала выбрать
видимость `Private`.

## 4. Подключите GitHub и отправьте код

Замените `<USER>` и `<REPOSITORY>`:

```bash
git remote add origin https://github.com/<USER>/<REPOSITORY>.git
git push -u origin main
```

## Модель

`gemma-4-E2B-it.litertlm` нельзя отправлять обычным Git: файл весит около 2,59 ГБ.
Разработчик должен получить модель отдельно и поместить её в:

```text
app/src/main/assets/gemma-4-E2B-it.litertlm
```

Не используйте `git add -f` для модели, APK или каталогов `build`.
