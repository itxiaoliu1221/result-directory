# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# The app is served publicly under /front/ by the host nginx.
ARG VITE_APP_BASE=/front/
ENV VITE_APP_BASE=$VITE_APP_BASE
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
