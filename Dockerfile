# Official Cobalt API image; upstream: https://github.com/imputnet/cobalt (AGPL-3.0)
FROM ghcr.io/imputnet/cobalt:11

# Render routes traffic to PORT; Cobalt listens on API_PORT.
# Set API_URL in Render to the public service URL, ending in a slash.
EXPOSE 10000
CMD ["sh", "-c", "export API_PORT=\"${PORT:-10000}\"; export API_URL=\"${API_URL:-http://localhost:${PORT:-10000}/}\"; exec node src/cobalt"]
