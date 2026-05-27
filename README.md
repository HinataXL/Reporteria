# 📊 Sistema de Reportería para Skill de Soporte

# 📊 Sistema de Reportería de Soporte

Sistema web desarrollado con Spring Boot para la gestión y monitoreo de conversaciones de soporte, incluyendo autenticación segura, dashboard en tiempo real, reportería y análisis con IA.

---

# 🚀 Tecnologías utilizadas

- Java 21
- Spring Boot 4
- Spring Security
- Thymeleaf
- PostgreSQL
- Supabase
- TailwindCSS
- Chart.js
- OpenAI API
- ngrok
- Maven

---

# 🔐 Funcionalidades principales

## Autenticación y seguridad

- Login personalizado
- Roles:
  - ADMIN
  - SUPERVISOR
  - AGENTE
- Protección por rutas
- 2FA (OTP con aplicación autenticadora)
- Sesiones protegidas
- Logout seguro

---

# 👥 Gestión de usuarios

- Creación de usuarios
- Roles dinámicos
- Contraseñas cifradas con BCrypt
- Lectura de usuarios desde Supabase

---

# 💬 Gestión de conversaciones

- Crear conversaciones
- Editar conversaciones
- Ver detalle completo
- Exportar CSV
- Estados:
  - Pendiente
  - En proceso
  - Resuelto
  - Escalado
  - Cerrado
- Prioridades:
  - Baja
  - Media
  - Alta
  - Crítica

---

# 📈 Dashboard Supervisor

Dashboard dinámico con métricas en tiempo real:

- Conversaciones por agente
- Conversaciones por canal
- Conversaciones por prioridad
- Productividad diaria
- Tiempo promedio de gestión
- Conversaciones pendientes
- Conversaciones resueltas
- Conversaciones escaladas

## Características visuales

- Diseño glassmorphism
- Chart.js
- Gradientes
- Animaciones
- Actualización automática vía polling API

---

# 🤖 Inteligencia Artificial

Integración con OpenAI para:

- Análisis automático de métricas
- Reportes operativos
- Riesgos operacionales
- Recomendaciones automáticas
- Puntos de mejora

---

# 🌐 Acceso público con ngrok

El sistema puede exponerse públicamente usando ngrok para:

- QA
- Demos
- Pruebas móviles
- Testing remoto

---

# ⚙️ Variables de entorno

## OpenAI

```env
OPENAI_API_KEY=tu_api_key
# 👨‍💻 Autor

**Erick Alejandro Pedroza Miguel**

Proyecto enfocado en reportería operativa para skills de soporte multicanal.

---

# 📄 Licencia

Proyecto privado en desarrollo.
