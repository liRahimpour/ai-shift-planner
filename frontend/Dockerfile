# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
FROM node:26-alpine AS build
WORKDIR /build

# Dependencies first, so a source change does not invalidate the install layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Type errors must fail the image build, not surface in the browser.
RUN npm run typecheck && npm run test && npx vite build

# ---- Runtime stage --------------------------------------------------------
#
# nginx-unprivileged, NOT the stock nginx image with a `USER` line bolted on.
#
# The stock image expects its entrypoint to start as root: that entrypoint renders the
# templates into /etc/nginx/conf.d and nginx itself creates /var/cache/nginx/*, dropping
# privileges afterwards via the `user` directive. Adding `USER nginx` to it breaks both at
# once - "conf.d is not writable" followed by mkdir() /var/cache/nginx/client_temp failing
# with permission denied, and a container that restart-loops.
#
# This variant is built for the job: it already runs as uid 101, listens on 8080, and owns
# the directories it needs to write.
FROM nginxinc/nginx-unprivileged:1.31-alpine AS runtime

# Files under /etc/nginx/templates get environment-variable substitution at startup, which
# is how BACKEND_HOST/BACKEND_PORT reach the config without being baked into the image.
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html

# NGINX_ENVSUBST_FILTER restricts substitution to BACKEND_* variables. Without it envsubst
# considers every environment variable, so a stray var named `host` or `scheme` would
# silently blank out nginx's own $host / $scheme and yield a config that is syntactically
# valid but wrong - the worst kind of breakage to debug.
ENV BACKEND_HOST=backend
ENV BACKEND_PORT=8080
ENV NGINX_ENVSUBST_FILTER="^BACKEND_"

# 8080 rather than 80: ports below 1024 need privileges this container does not have.
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
