# STAGE 1: Build the React application

# Use Node.js 22 to install dependencies and build React
FROM node:22-alpine AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the dependency files first
# This allows Docker to reuse cached dependency layers
COPY package.json package-lock.json ./

# Install the exact dependencies from package-lock.json
RUN npm ci

# Copy the frontend source code and configuration
COPY . .

# Vite bakes VITE_* values into the bundle at build time, so the public
# Brandfetch Logo API client ID must be passed as a build argument
# (compose.yaml reads it from the root .env). Without it, logos fall back to initials.
ARG VITE_BRANDFETCH_CLIENT_ID=""
ENV VITE_BRANDFETCH_CLIENT_ID=$VITE_BRANDFETCH_CLIENT_ID

# Build the production React application
# Vite generates static files inside /app/dist
RUN npm run build


# STAGE 2: Serve the application using Nginx

# Use a lightweight Nginx image
FROM nginx:alpine

# Copy the production build from the Node.js stage
# Nginx serves these files to the browser
COPY --from=build /app/dist /usr/share/nginx/html

# Copy our custom Nginx configuration
# This will handle React routing and API requests
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Nginx listens on port 80 inside the container
# Docker Compose will later map localhost:5180 to port 80
EXPOSE 80

# Start Nginx and keep it running in the foreground
CMD ["nginx", "-g", "daemon off;"]
