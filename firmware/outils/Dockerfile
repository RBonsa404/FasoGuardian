# Outillage des essais sur machine hôte, pour un poste sans compilateur C :
#   docker build -t fasoguardian-firmware-hote firmware/outils
#   docker run --rm -v "$PWD/firmware:/src" -w /src fasoguardian-firmware-hote make essais
FROM alpine:3.20
RUN apk add --no-cache build-base
