FROM public.ecr.aws/docker/library/node:22-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM public.ecr.aws/docker/library/nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
