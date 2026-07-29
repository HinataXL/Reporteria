# Skill Soporte - Reporteria Operativa

Aplicacion web para gestionar, monitorear y reportar conversaciones de soporte multicanal. El sistema centraliza conversaciones, usuarios, dashboards por rol, auditoria, exportaciones y analisis operativo con Gemini.

## Estado del Proyecto

Proyecto privado en desarrollo activo.

Funcionalidades principales disponibles:

- Autenticacion con Spring Security.
- Roles ADMIN, SUPERVISOR y AGENTE.
- Seguridad 2FA con aplicacion autenticadora.
- Gestion de usuarios administrativos.
- Gestion de conversaciones de soporte.
- Dashboard de supervisor con metricas operativas.
- Dashboard de agente con metricas asignadas.
- Indicadores por estado: pendientes, resueltas, escaladas y cerradas.
- Top 10 asuntos y top 10 clientes.
- Vista 360 de clientes.
- Horas pico de atencion.
- Tendencias semanales y comparativa contra periodo anterior.
- Actividad en tiempo real mediante WebSocket.
- Keep-alive de sesion para evitar expiraciones durante gestion.
- Exportacion CSV.
- Reporte PDF supervisor.
- Auditoria de acciones del sistema.
- Monitoreo de uso de Gemini desde panel admin.
- Sincronizacion de clientes desde Zoho Desk.

## Tecnologias

- Java 21
- Spring Boot 4.0.6
- Spring MVC
- Spring Security
- Spring Data JPA
- Thymeleaf
- PostgreSQL
- WebSocket/STOMP
- Maven
- Docker
- TailwindCSS
- Chart.js / ApexCharts
- Gemini API
- OpenHTMLToPDF

## Estructura Principal

```text
src/main/java/com/erick/soporte
  config/        Configuracion WebSocket
  controller/    Controladores MVC y API
  entity/        Entidades JPA
  repository/    Repositorios Spring Data
  security/      Login, roles, 2FA y filtros de seguridad
  service/       Servicios de auditoria, reportes, IA y tiempo real

src/main/resources
  templates/     Vistas Thymeleaf
  static/        CSS, JS e imagenes
  application.properties
```

## Roles y Accesos

### ADMIN

- Acceso a panel administrador.
- Gestion de usuarios.
- Auditoria del sistema.
- Estado y uso de Gemini.
- Exportacion CSV.
- Acceso a dashboards operativos.

### SUPERVISOR

- Dashboard supervisor.
- Reportes y exportaciones.
- Acciones masivas sobre conversaciones.
- Vista global de metricas del equipo.

### AGENTE

- Dashboard agente.
- Creacion y seguimiento de conversaciones.
- Vista limitada a conversaciones asignadas.

## Rutas Relevantes

```text
/login
/conversations
/conversations/create
/conversations/export/csv
/supervisor/dashboard
/agent/dashboard
/admin/dashboard
/admin/gemini
/admin/logs
/admin/zoho
/users
/settings/2fa
/profile
```

APIs principales:

```text
/api/conversations/save
/api/dashboard/metrics
/api/dashboard/ai-report
/api/dashboard/issue-trends
/api/dashboard/client-360
/api/dashboard/peak-hour
/api/dashboard/status-conversations
/api/agent-dashboard/status-conversations
/api/session/keep-alive
/api/webhooks/qpaypro
/api/clients/search
```

## Variables de Entorno

La aplicacion usa variables de entorno para la conexion a base de datos, Gemini y configuracion general.

```env
PORT=8080

DATABASE_URL=jdbc:postgresql://host:5432/database
DATABASE_USERNAME=usuario
DATABASE_PASSWORD=password

GEMINI_API_KEY=tu_api_key

ZOHO_DESK_BASE_URL=https://desk.zoho.com
ZOHO_ACCOUNTS_URL=https://accounts.zoho.com
ZOHO_DESK_ORG_ID=tu_org_id
ZOHO_CLIENT_ID=tu_client_id
ZOHO_CLIENT_SECRET=tu_client_secret
ZOHO_REFRESH_TOKEN=tu_refresh_token
ZOHO_DEFAULT_DEPARTMENT_ID=department_id_de_zoho

MAIL_HOST=smtp.zoho.com
MAIL_PORT=587
MAIL_USERNAME=tu_correo_zoho
MAIL_PASSWORD=tu_password_smtp_o_app_password
REPORT_MAIL_FROM=tu_correo_zoho
REPORT_MAIL_TO=destino@zoho.com
REPORT_MAIL_CC=copia@outlook.com

LOGIN_ALERT_ENABLED=true
LOGIN_ALERT_USER=pablo.flores@fixss.com
LOGIN_ALERT_EMAIL_TO=correo_que_recibe_la_alerta@dominio.com
```

Configuracion actual en `application.properties`:

```properties
spring.datasource.url=${DATABASE_URL}&prepareThreshold=0
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
gemini.api.key=${GEMINI_API_KEY}
gemini.model=gemini-2.5-flash
zoho.desk.base-url=${ZOHO_DESK_BASE_URL:}
zoho.accounts.url=${ZOHO_ACCOUNTS_URL:}
zoho.desk.org-id=${ZOHO_DESK_ORG_ID:}
zoho.client-id=${ZOHO_CLIENT_ID:}
zoho.client-secret=${ZOHO_CLIENT_SECRET:}
zoho.refresh-token=${ZOHO_REFRESH_TOKEN:}
zoho.default-department-id=${ZOHO_DEFAULT_DEPARTMENT_ID:}
spring.mail.host=${MAIL_HOST:smtp.zoho.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
report.mail.from=${REPORT_MAIL_FROM:}
report.mail.to=${REPORT_MAIL_TO:}
report.mail.cc=${REPORT_MAIL_CC:}
login.alert.enabled=${LOGIN_ALERT_ENABLED:true}
login.alert.user=${LOGIN_ALERT_USER:pablo.flores@fixss.com}
login.alert.to=${LOGIN_ALERT_EMAIL_TO:}
app.allowed-email-domain=@fixss.com
server.servlet.session.timeout=10m
```

Si se requiere cambiar el modelo de Gemini, actualizar `gemini.model` en `application.properties`.

Nota: `DATABASE_URL` debe incluir el prefijo JDBC, por ejemplo:

```text
jdbc:postgresql://localhost:5432/soporte
```

## Ejecucion Local

Requisitos:

- Java 21
- Maven 3.9+
- PostgreSQL

Compilar:

```bash
./mvnw clean package -DskipTests
```

Ejecutar:

```bash
./mvnw spring-boot:run
```

En Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

La aplicacion queda disponible en:

```text
http://localhost:8080
```

## Docker

Construir imagen:

```bash
docker build -t skill-soporte .
```

Ejecutar contenedor:

```bash
docker run --rm -p 8080:8080 \
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/soporte" \
  -e DATABASE_USERNAME="usuario" \
  -e DATABASE_PASSWORD="password" \
  -e GEMINI_API_KEY="tu_api_key" \
  skill-soporte
```

## Base de Datos

El proyecto usa JPA con:

```properties
spring.jpa.hibernate.ddl-auto=update
```

Tablas principales:

- `users`
- `roles`
- `conversations`
- `audit_logs`
- `support_clients`
- `issue_type`
- `departments`
- `rejection_codes`
- `webhook_events`

Importante: en PostgreSQL, si se cargan usuarios manualmente, validar que la secuencia de `users.id` este sincronizada con el valor maximo existente.

## Seguridad

- Las contrasenas se almacenan con BCrypt.
- El acceso se controla por roles.
- 2FA utiliza TOTP.
- Las rutas `/api/webhooks/qpaypro` y `/ws/**` se excluyen de CSRF por requerimientos de integracion.
- El dominio permitido para login se controla con `app.allowed-email-domain`.

## IA con Gemini

Gemini se usa para:

- Resumen operativo del dashboard.
- Analisis de tendencias por asunto.
- Apoyo en reportes supervisor.
- Monitoreo desde `/admin/gemini`.

El servicio esta preparado para no bloquear el arranque si Gemini no esta disponible; en ese caso se muestra un fallback operativo.

## Integracion Zoho Desk

Zoho Desk se usa como fuente externa de contactos/clientes. La sincronizacion se ejecuta desde:

```text
/admin/zoho
```

La integracion:

- Renueva el access token con `ZOHO_REFRESH_TOKEN`.
- Consulta contactos de Zoho Desk usando `orgId`.
- Guarda clientes en la tabla local `support_clients`.
- Permite busqueda rapida desde el formulario de nueva conversacion mediante `/api/clients/search`.
- Permite crear tickets en Zoho Desk desde el detalle de una conversacion guardada.

Scopes recomendados en Zoho API Console:

```text
Desk.contacts.READ,Desk.basic.READ,Desk.tickets.CREATE,Desk.tickets.READ
```

Si se requiere crear o actualizar contactos desde Reporteria hacia Zoho, agregar:

```text
Desk.contacts.CREATE,Desk.contacts.UPDATE
```

## Auditoria

La auditoria registra acciones relevantes del sistema en `audit_logs`, incluyendo:

- Usuario
- Rol
- Accion
- Modulo
- Descripcion
- IP
- User-Agent
- Fecha

La vista administrativa esta disponible en:

```text
/admin/logs
```

## Tiempo Real

El sistema usa WebSocket/STOMP para publicar eventos hacia dashboards y actividad en tiempo real.

Endpoint:

```text
/ws
```

Topics usados:

```text
/topic/dashboard-events
/topic/webhook-events
```

## Exportaciones y Reportes

- CSV de conversaciones: `/conversations/export/csv`
- Envio manual de CSV por correo: `/admin/reports/email-csv`
- PDF supervisor: `/supervisor/report/pdf`

El CSV incluye solo la informacion solicitada para reporte operativo: cliente, telefono, asunto, fecha de inicio, fecha guardado y observaciones.

El envio manual por correo permite seleccionar un rango de fechas, adjuntar el CSV y enviarlo desde una cuenta Zoho Mail con copia opcional a otra direccion. Para probarlo se deben configurar `MAIL_USERNAME`, `MAIL_PASSWORD`, `REPORT_MAIL_TO` y `REPORT_MAIL_CC`.

## Despliegue

El proyecto incluye `Dockerfile` multi-stage:

1. Compila con Maven y Java 21.
2. Ejecuta el `.jar` final sobre Eclipse Temurin 21 JRE.

Puerto expuesto:

```text
8080
```

## Mantenimiento

Antes de subir cambios:

```bash
./mvnw -DskipTests compile
```

Recomendaciones:

- No subir credenciales ni archivos locales.
- Mantener variables sensibles fuera del repositorio.
- Revisar migraciones o cambios de entidades antes de desplegar.
- Validar creacion de usuarios, dashboards y exportaciones despues de cambios de base de datos.

## Autor

Erick Alejandro Pedroza Miguel

Proyecto enfocado en reporteria operativa y gestion de soporte multicanal.

## Licencia

Proyecto privado. Todos los derechos reservados.
