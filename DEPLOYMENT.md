# Deployment Guide: Live on Render (100% Free)

This guide walks you through deploying the **VeloxOrder Engine** to the cloud using **Render** on their free tier.

---

## Step 1: Push Your Code to GitHub

Render deploys directly from GitHub. If you haven't put your project on GitHub yet:

1. **Initialize Git** in the project root:
   ```bash
   git init
   ```
2. **Add a `.gitignore`** to exclude binary build artifacts and target directories (already configured or add standard Java ignore):
   Create a file named `.gitignore` with:
   ```
   target/
   *.class
   .idea/
   *.iml
   .DS_Store
   ```
3. **Commit your files**:
   ```bash
   git add .
   git commit -m "feat: complete order engine with Docker configurations"
   ```
4. **Push to a new GitHub repository**:
   - Go to GitHub, create a new public or private repository (do **not** check "Initialize with README").
   - Link it and push:
     ```bash
     git branch -M main
     git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
     git push -u origin main
     ```

---

## Step 2: Deploy to Render

1. Sign up or log in at **[Render](https://render.com/)** (completely free).
2. Click **New +** (top right) and choose **Web Service**.
3. Select **Connect a repository** and choose your `velox-order-engine` repository.
4. Fill in the configuration details:
   - **Name:** `velox-order-engine` (or any custom name)
   - **Region:** Choose the region closest to you (e.g. Singapore, Oregon, Frankfurt)
   - **Branch:** `main`
   - **Runtime:** **Docker** (Render will automatically detect our `Dockerfile` and execute a multi-stage compilation!)
   - **Instance Type:** **Free** ($0/month, 512MB RAM)
5. Click **Deploy Web Service** at the bottom of the page.

---

## Step 3: Access and Test Your Dashboard

1. Wait for Render to build the Docker image (typically takes 3 to 5 minutes).
2. Once the build is complete, you will see a green **Live** badge.
3. Click the public HTTPS link provided at the top left of the Render panel (e.g., `https://velox-order-engine.onrender.com`).
4. **Simulate Concurrent Load:**
   - Click the **Seed Catalog** button to populate the in-memory demo database.
   - Go to the **Simulation Panel**, select a product, set the number of concurrent orders (e.g., 20), and launch the traffic load!
   - View the processing threads and logs populate dynamically in the dashboard log terminal.

---

## Step 4 (Optional): Connecting to a Live MySQL Database

If you want persistent data storage rather than an in-memory database that resets when the server sleeps:

1. Create a free MySQL database on **[Aiven.io](https://aiven.io/)** or **[Clever Cloud](https://www.clever-cloud.com/)**.
2. Retrieve your database **Host**, **Port**, **Database Name**, **Username**, and **Password**.
3. In the Render Dashboard, go to your Web Service page and click the **Environment** tab.
4. Add the following **Environment Variables**:
   - `SPRING_PROFILES_ACTIVE` = `default`
   - `SPRING_DATASOURCE_URL` = `jdbc:mysql://<YOUR_DATABASE_HOST>:<PORT>/<DATABASE_NAME>?useSSL=true`
   - `SPRING_DATASOURCE_USERNAME` = `<YOUR_DATABASE_USER>`
   - `SPRING_DATASOURCE_PASSWORD` = `<YOUR_DATABASE_PASSWORD>`
5. Click **Save Changes**. Render will automatically redeploy the application, connecting it to your cloud MySQL instance!
