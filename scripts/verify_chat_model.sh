#!/usr/bin/env bash
# Verify that an Ollama model works correctly with the /api/chat endpoint + tools.
# Usage: ./scripts/verify_chat_model.sh [model_name] [ollama_base_url]
set -euo pipefail

MODEL="${1:-granite4:latest}"
BASE_URL="${2:-http://localhost:11434}"
CHAT_URL="${BASE_URL}/api/chat"
PASS=0
FAIL=0

echo "=== Verifying model: ${MODEL} ==="
echo "    Ollama URL: ${BASE_URL}"
echo ""

# --- Check 1: Basic chat completion ---
echo "--- Check 1: Basic chat completion ---"
RESPONSE=$(curl -sf "${CHAT_URL}" -d "$(cat <<EOF
{
  "model": "${MODEL}",
  "stream": false,
  "messages": [
    {"role": "system", "content": "You are a helpful Minecraft villager named Bob. Reply in 1-2 sentences."},
    {"role": "user", "content": "Hello! What do you sell?"}
  ]
}
EOF
)" 2>&1) || { echo "FAIL - HTTP request failed"; FAIL=$((FAIL + 1)); RESPONSE=""; }

if [ -n "${RESPONSE}" ]; then
	CONTENT=$(echo "${RESPONSE}" | python3 -c "import sys,json; msg=json.load(sys.stdin).get('message',{}); print(msg.get('content',''))" 2>/dev/null || echo "")

	if [ -z "${CONTENT}" ]; then
		echo "FAIL - Empty response content"
		FAIL=$((FAIL + 1))
	else
		echo "Response: ${CONTENT}"

		# Check for leaked special tokens
		LEAKED=false
		for TOKEN in '<|channel|>' '<|start_header_id|>' '<|end_header_id|>' '<|eot_id|>' '<|start_of_turn|>' '<|end_of_turn|>'; do
			if echo "${CONTENT}" | grep -qF "${TOKEN}"; then
				echo "FAIL - Leaked special token: ${TOKEN}"
				LEAKED=true
			fi
		done

		if [ "${LEAKED}" = false ]; then
			echo "PASS"
			PASS=$((PASS + 1))
		else
			FAIL=$((FAIL + 1))
		fi
	fi
fi

echo ""

# --- Check 2: Tool calling ---
echo "--- Check 2: Tool calling ---"
TOOL_RESPONSE=$(curl -sf "${CHAT_URL}" -d "$(cat <<EOF
{
  "model": "${MODEL}",
  "stream": false,
  "messages": [
    {"role": "system", "content": "You are a Minecraft villager. Use tools to answer questions about blocks and items."},
    {"role": "user", "content": "Are there any diamond blocks nearby?"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_nearby_blocks",
        "description": "Search for blocks near the villager's current position.",
        "parameters": {
          "type": "object",
          "properties": {
            "query": {
              "type": "string",
              "description": "The type of block to search for"
            }
          },
          "required": ["query"]
        }
      }
    }
  ]
}
EOF
)" 2>&1) || { echo "FAIL - HTTP request failed"; FAIL=$((FAIL + 1)); TOOL_RESPONSE=""; }

if [ -n "${TOOL_RESPONSE}" ]; then
	HAS_TOOL_CALLS=$(echo "${TOOL_RESPONSE}" | python3 -c "
import sys, json
msg = json.load(sys.stdin).get('message', {})
tc = msg.get('tool_calls', [])
if tc and len(tc) > 0:
    call = tc[0]
    fn = call.get('function', {})
    name = fn.get('name', '')
    args = fn.get('arguments', {})
    print(f'YES name={name} args={json.dumps(args)}')
else:
    print('NO')
" 2>/dev/null || echo "ERROR")

	if [[ "${HAS_TOOL_CALLS}" == YES* ]]; then
		echo "Tool call: ${HAS_TOOL_CALLS#YES }"
		echo "PASS"
		PASS=$((PASS + 1))
	elif [[ "${HAS_TOOL_CALLS}" == "NO" ]]; then
		# Check if tool call was faked in content
		TOOL_CONTENT=$(echo "${TOOL_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',{}).get('content',''))" 2>/dev/null || echo "")
		if echo "${TOOL_CONTENT}" | grep -q '"name"' && echo "${TOOL_CONTENT}" | grep -q '"arguments"'; then
			echo "FAIL - Model emitted tool call as plain text instead of native tool_calls"
			echo "Content: ${TOOL_CONTENT}"
		else
			echo "FAIL - No tool_calls in response (model may not support function calling)"
			echo "Content: ${TOOL_CONTENT}"
		fi
		FAIL=$((FAIL + 1))
	else
		echo "FAIL - Could not parse response"
		FAIL=$((FAIL + 1))
	fi
fi

echo ""
echo "=== Results: ${PASS} PASS, ${FAIL} FAIL ==="
exit ${FAIL}
