# VeloxOrder Engine 🚀

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/FakkirappaSS/velox-order-engine)

A high-throughput, non-blocking Retail Order Processing Engine simulating a high-traffic checkout pipeline (like Target's checkout system). The application immediately returns an order confirmation while executing heavy operations (physical stock database deduction, mock payment loop, and warehouse notifications) concurrently in the background.

## Key Features
- **Fast-Path Inventory Validation:** An in-memory, thread-safe cache (`ConcurrentHashMap`) resolves inventory checks synchronously to filter out invalid or out-of-stock items, protecting the database under flash-sale spikes.
- **Asynchronous Execution Pipeline:** Leverages a robust Spring Boot task executor pool to run stock deduction, payment simulation (1.5s latency), and warehouse notifications concurrently.
- **Database Consistency:** Uses Hibernate `@Version` Optimistic Locking to guarantee that concurrent sales of the same product are properly validated, retrying up to 3 times on conflict before failing.
- **Real-Time Interactive Dashboard:** Visualizes thread pool usage, queue sizes, order transitions, and cache vs database stock levels. Features a load simulator to fire up to 100+ concurrent checkout requests with a single click.

---

## 🛠️ Deployment on Render (100% Free)

You can deploy this project live on Render with **one click**:

1. Click the **Deploy to Render** button above.
2. Sign in to your Render account (completely free).
3. Click **Apply**.
4. Once Render completes the Docker build, click the URL provided in the dashboard to open your live application!
