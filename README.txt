Grocio Android Application (Android Studio)
│
├── User Login
│   │
│   └── Firebase Authentication
│
├── Upload Receipt Image
│   │
│   └── Firebase Cloud Storage
│       │
│       └── Receipt Image Stored
│
├── Firebase Cloud Function Trigger
│   │
│   ├── Detect New Receipt Upload
│   │
│   ├── Retrieve Image URL
│   │
│   └── Send Request to GCP Gemma Model
│       │
│       ├── Prompt Engineering
│       │   │
│       │   ├── Analyse receipt image
│       │   ├── Extract merchant details
│       │   ├── Extract date
│       │   ├── Extract products
│       │   ├── Extract quantities
│       │   ├── Extract prices
│       │   └── Format response as JSON
│       │
│       └── Return Structured JSON Response
│
├── Firebase Cloud Function Receives Gemma Output
│   │
│   ├── Validate JSON response
│   │
│   ├── Process / Transform Data
│   │
│   └── Upload Data
│       │
│       ├── Firebase Realtime Database
│       │   ├── Receipt processing status
│       │   ├── Live receipt data
│       │   └── Real-time updates
│       │
│       └── Cloud Firestore
│           ├── User receipt records
│           ├── Product relationships
│           ├── Pantry items
│           ├── Shopping lists
│           ├── Categories
│           └── Historical data
│
└── Grocio Android Application
    │
    ├── Reads Firebase data
    │
    ├── Displays:
    │   ├── Receipt image
    │   ├── Extracted items
    │   ├── Prices
    │   ├── Pantry updates
    │   └── Shopping insights
    │
    └── Real-time UI updates