# 📊 Sistema de Reportería para Skill de Soporte

Aplicación web desarrollada con **Spring Boot + Thymeleaf + MySQL** para registrar manualmente conversaciones de soporte y generar reportería operativa.

---

# 🚀 Objetivo del proyecto

Centralizar el registro de conversaciones manejadas por agentes de soporte en distintos canales:

- WhatsApp
- Facebook
- Instagram
- Web Chat
- Otros canales multicanal

El sistema permitirá:
- Registrar conversaciones
- Consultar historial
- Visualizar métricas
- Exportar reportes
- Administrar agentes y estados

---

# 🛠️ Tecnologías utilizadas

| Tecnología | Uso |
|---|---|
| Java 21 | Backend |
| Spring Boot | Framework MVC |
| Thymeleaf | Renderizado HTML |
| MySQL | Base de datos |
| TailwindCSS | Diseño UI |
| Maven | Gestión de dependencias |
| Git/GitHub | Control de versiones |
| IntelliJ IDEA | IDE principal |

---

# 📁 Estructura del proyecto

```bash
src/
├── main/
│   ├── java/
│   │   └── com/erick/soporte/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── entity/
│   │       └── SoporteApplication.java
│   │
│   └── resources/
│       ├── static/
│       │   ├── img/
│       │   ├── css/
│       │   └── js/
│       │
│       ├── templates/
│       │   └── index.html
│       │
│       └── application.properties
```

---

# ⚙️ Configuración inicial

## 1. Clonar repositorio

```bash
git clone https://github.com/TU_USUARIO/soporte-multicanal.git
```

---

## 2. Entrar al proyecto

```bash
cd soporte-multicanal
```

---

## 3. Configurar MySQL

Crear base de datos:

```sql
CREATE DATABASE soporte_db;
```

---

## 4. Configurar application.properties

Ruta:

```bash
src/main/resources/application.properties
```

Ejemplo:

```properties
spring.application.name=soporte

server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/soporte_db
spring.datasource.username=root
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

spring.thymeleaf.cache=false
```

---

# ▶️ Ejecutar aplicación

## Desde IntelliJ

Ejecutar:

```text
SoporteApplication.java
```

---

## Desde terminal

Linux/Mac:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw spring-boot:run
```

---

# 🌐 Acceso local

```text
http://localhost:8080
```

---

# 🔥 Hot Reload

El proyecto utiliza:

```xml
spring-boot-devtools
```

Para recarga automática durante desarrollo.

---

# 📌 Funcionalidades planeadas

## MVP inicial

- [x] Dashboard principal
- [x] Integración MySQL
- [x] MVC Spring Boot
- [ ] CRUD conversaciones
- [ ] Registro manual de chats
- [ ] Tabla historial
- [ ] Filtros por fecha/agente/canal
- [ ] Exportar Excel
- [ ] Exportar PDF

---

# 🔐 Futuras mejoras

- Login y autenticación
- Roles de usuario
- Dashboard en tiempo real
- Integración API multicanal
- Métricas avanzadas
- WebSockets
- Reportería automática

---

# 🎨 Diseño UI

El frontend está basado en:
- TailwindCSS
- Glassmorphism
- Dashboard interactivo
- Responsive Design

---

# 👨‍💻 Autor

**Erick Alejandro Pedroza Miguel**

Proyecto enfocado en reportería operativa para skills de soporte multicanal.

---

# 📄 Licencia

Proyecto privado en desarrollo.
