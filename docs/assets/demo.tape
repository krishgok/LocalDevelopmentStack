# vhs tape — records the canonical 30-second LocalDevelopmentStack flow
#
# To produce the GIF that the README embeds:
#   1. Install vhs:   brew install vhs        (macOS)
#                     winget install charmbracelet.vhs   (Windows)
#                     go install github.com/charmbracelet/vhs@latest   (any)
#   2. Run:           vhs docs/assets/demo.tape
#   3. Commit the resulting docs/assets/demo.gif
#
# The script generates a Go + Postgres stack, brings up the containers, and
# verifies /health returns {"status":"ok"} — the same "first stack" flow the
# README walks through.

Output docs/assets/demo.gif

Set FontSize    16
Set Width       1100
Set Height      640
Set Theme       "Catppuccin Mocha"
Set Padding     20
Set TypingSpeed 35ms
Set Shell       bash

# 1. Generate the stack
Type "localdevstack --service go --database postgres --output ./demo --name my-api"
Enter
Sleep 2s

# 2. Inspect what got written
Type "cd demo && ls"
Enter
Sleep 1500ms

# 3. Boot the stack (detached so the recording keeps moving)
Type "docker-compose up --build -d"
Enter
Sleep 25s

Type "docker-compose ps"
Enter
Sleep 2s

# 4. Verify — retry until /health is reachable so the JSON response is captured
Type "curl -s --retry 20 --retry-connrefused --retry-delay 1 http://localhost:8080/health"
Enter
Sleep 5s

Type "# Full stack — service + Postgres — running locally in under 30 seconds."
Enter
Sleep 3500ms
