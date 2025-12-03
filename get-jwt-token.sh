#!/bin/bash

# Скрипт для получения JWT токена для тестирования

echo "======================================"
echo "JWT Token Generator"
echo "======================================"
echo ""

# Default values
API_URL="http://localhost:8080"
USERNAME="${1:-admin}"
PASSWORD="${2:-admin123}"

echo "🔐 Попытка авторизации..."
echo "   Username: $USERNAME"
echo "   Password: ******"
echo ""

# Try admin login first
echo "Попытка входа как admin..."
RESPONSE=$(curl -s -X POST "$API_URL/api/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

# Check if successful
TOKEN=$(echo "$RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

# If admin login failed, try client login
if [ -z "$TOKEN" ]; then
    echo "❌ Admin login failed, trying client login..."
    RESPONSE=$(curl -s -X POST "$API_URL/api/client/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

    TOKEN=$(echo "$RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

    # Also try "token" field
    if [ -z "$TOKEN" ]; then
        TOKEN=$(echo "$RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    fi
fi

if [ -z "$TOKEN" ]; then
    echo ""
    echo "❌ Ошибка: Не удалось получить токен!"
    echo ""
    echo "Ответ сервера:"
    echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
    echo ""
    echo "Возможные причины:"
    echo "  1. Backend не запущен (запустите: ./mvnw spring-boot:run)"
    echo "  2. Неверные учетные данные"
    echo "  3. API URL неправильный"
    echo ""
    echo "Использование:"
    echo "  ./get-jwt-token.sh [username] [password]"
    echo ""
    echo "Примеры:"
    echo "  ./get-jwt-token.sh admin admin123"
    echo "  ./get-jwt-token.sh client client123"
    exit 1
fi

echo ""
echo "✅ Токен получен успешно!"
echo ""
echo "======================================"
echo "JWT TOKEN:"
echo "======================================"
echo "$TOKEN"
echo ""
echo "======================================"
echo "Использование в curl:"
echo "======================================"
echo "curl -H \"Authorization: Bearer $TOKEN\" \\"
echo "  http://localhost:8080/api/vehicles/positions"
echo ""
echo "======================================"
echo "Для HTML страницы:"
echo "======================================"
echo "1. Откройте gps-test-map.html"
echo "2. Вставьте токен в поле 'JWT Token'"
echo "3. Нажмите 'Загрузить данные'"
echo ""

# Save to file
echo "$TOKEN" > .jwt-token
echo "💾 Токен сохранен в файл .jwt-token"
echo ""

# Copy to clipboard if xclip is available
if command -v xclip &> /dev/null; then
    echo "$TOKEN" | xclip -selection clipboard
    echo "📋 Токен скопирован в буфер обмена!"
elif command -v pbcopy &> /dev/null; then
    echo "$TOKEN" | pbcopy
    echo "📋 Токен скопирован в буфер обмена!"
fi
