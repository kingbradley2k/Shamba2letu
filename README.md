
Problem Statement

Accurate land measurement is a critical activity in land management, real estate development, agriculture, and infrastructure planning. However, many land surveyors in Kenya and other developing regions continue to rely on outdated tools, manual calculations, and equipment that is either too expensive or difficult to operate. This creates delays, inconsistencies, and errors in land measurements, leading to disputes, financial losses, and inefficiencies in land administration systems.
Traditional surveying methods rely heavily on manual note-taking, physical tools like tapes and compasses. Surveyors often work in remote environments where access to stable internet is limited. Inaccurate GPS readings, loss of field data, and difficulty in generating digital records further complicate the process. As a result, survey reports may contain errors and require repeated measurements all of which reduce productivity and erode trust among clients, landowners, and government authorities.
The absence of a reliable, mobile-based land measuring solution means that surveyors remain vulnerable to inefficiencies and data losses. Without a digital and GPS-enabled system, the risk of boundary disputes increases due to inaccurate or inconsistent measurements. Poor documentation and manual reporting slow down land registration processes, contributing to conflicts between landowners, delays in development projects, and increased operational costs for surveying professionals.
To address these challenges, there is a need for a modern, portable land measuring application that leverages smartphone GPS, digital mapping, and offline capabilities that allows surveyors to calculate land area instantly. This reduce human error, minimize disputes, and modernize the surveying process for improved land management and decision-making.
 
 
 1. Functional Requirements (What the system should do)
Core Measurement Features
GPS-Based Measurement:
The app should capture location coordinates using the device’s GPS sensor.
It should calculate distances and areas between selected points.
Manual Point Entry:
Users should be able to manually enter coordinates or import them from external files (e.g., CSV, KML).
Polygon and Line Creation:
The app should allow users to draw lines or polygons to define land boundaries on the map.
Real-Time Tracking:
As the surveyor moves, the app should update the map and measurements dynamically.
Map Integration:
Integration with map APIs (Google Maps) to visualize the surveyed area.
Unit Conversion:
Support measurement units such as meters, acres, hectares, and square feet.
Save and Retrieve Projects:
Users should be able to save measured plots and reload them later.
Export Data:
Ability to export results in formats like PDF, CSV, or KML for sharing or reporting.
Offline Functionality:
The app should store map tiles and GPS data offline for remote area use.
User Authentication (optional):
Secure login and registration system for surveyors to manage their projects.
Report Generation:
Generate a summary report of the measured land (coordinates, area, perimeter, timestamp, etc.).
Error Handling:
Notify the user when GPS signal is weak, or data input is invalid.

3. Non-Functional Requirements (How the system performs)
   
Performance
The app should calculate distances and areas within 1–2 seconds for typical plots.
GPS data should update in real time (within 2–3 seconds of movement).
Security
User data and location data must be encrypted during storage and transmission.
Implement secure authentication (e.g., hashed passwords, token-based sessions).
Usability
The interface should be intuitive, allowing non-technical users (surveyors) to easily measure and save land.
The app should include visual cues and tooltips for navigation and map operations.
Reliability
The app must maintain accurate measurements even under weak GPS signals (with error margin less than 2%).
It should auto-save data during active measurement to prevent data loss if the app closes unexpectedly.
Availability
The app should function both online and offline, syncing when connectivity returns.
Scalability
The backend should handle multiple surveyors and projects without performance degradation.
Maintainability
The codebase should be modular and easy to update.
Efficiency
The app should be optimized to minimize battery drain during GPS usage.
Portability
The app should run smoothly on Android.
It should adapt to different screen sizes and resolutions.
