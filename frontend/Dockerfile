# Etapa 1: Compilación de la aplicación React con Node.js
FROM node:22-alpine AS builder
WORKDIR /app

# Instala dependencias
COPY package*.json ./
RUN npm ci

# Copia código fuente y compila la SPA
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# Etapa 2: Servidor web ligero con Nginx
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
