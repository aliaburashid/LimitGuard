# Use Python 3.11 with a lightweight Linux environment
# This is the base image for our ML Service container
FROM python:3.11-slim

# Set /app as the working directory inside the container
# All following commands will run relative to this directory
WORKDIR /app

# Copy the requirements.txt file from our ML Service folder
# into the container's /app directory
COPY requirements.txt .

# Install all Python libraries listed in requirements.txt
# --no-cache-dir prevents pip from storing downloaded package caches
# This helps reduce the final Docker image size
RUN pip install --no-cache-dir -r requirements.txt

# Copy main.py, which contains our FastAPI application
# into the container's working directory
COPY main.py .

# Copy the model folder containing our pretrained ML files
# These include annual_logistic_model.pkl and scoring_info.pkl
# The application needs these files to make predictions
COPY model ./model

# Document that our FastAPI application listens on port 8000
# EXPOSE does not publish the port to our Mac automatically
EXPOSE 8000

# Start the FastAPI application when the container runs
# uvicorn is the server used to run FastAPI applications
# main:app means the "app" object inside main.py
# 0.0.0.0 allows connections from outside the container
# Port 8000 is where our ML Service listens for requests
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
