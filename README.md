# Glassmark — Android starter

Aplicativo Android nativo (Kotlin + Jetpack Compose) com conceito visual Liquid Glass.

## Incluído
- Compartilhamento Android `ACTION_SEND` para salvar links
- Banco local Room
- Busca por URL/título
- Favoritos
- Adição manual de links
- Interface escura com superfícies translúcidas

## Abrir
1. Extraia o ZIP.
2. Abra a pasta `Glassmark` no Android Studio (versão recente).
3. Aguarde sincronização Gradle.
4. Execute em dispositivo/emulador Android.

## Próximas melhorias sugeridas
- Extração automática de metadados Open Graph (título, descrição e imagem)
- Tela de coleções e filtros
- Backup/restauração JSON
- Melhorias no tratamento de compartilhamentos de texto sem URL direta
- Polimento Liquid Glass e ícone adaptativo

Nota: este scaffold é código-fonte inicial. O APK ainda precisa ser compilado no Android Studio; não foi compilado/testado neste ambiente.

## Baixar o APK pronto
Vá na aba **Releases** do repositório e baixe o arquivo `Glassmark.apk` da versão mais recente (`v1.0`). No celular, permita a instalação de apps dessa origem quando o Android pedir.

O APK é gerado automaticamente pelo GitHub Actions a cada push na branch `main` (ou manualmente em Actions > Run workflow).

## Compilar sem Android Studio (GitHub Actions)
1. Crie um repositório no GitHub e envie o conteúdo deste ZIP.
2. Abra a aba **Actions** do repositório.
3. Se necessário, habilite workflows no repositório.
4. Execute **Build Glassmark APK** (Run workflow).
5. Ao terminar, abra a execução e baixe o artifact **Glassmark-debug-apk**.
6. Extraia o artifact e instale `app-debug.apk` no Android, permitindo instalação dessa origem quando solicitado.

O workflow compila um APK de debug para testes. Não é um APK assinado para publicação na Play Store.
