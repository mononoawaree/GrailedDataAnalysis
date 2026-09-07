import requests

url = "https://media-assets.grailed.com/prd/listing/temp/4f1ab17f1f194d68ab6902d8a22fea24"

def main():
    response = requests.get(url)
    with open("emdedding.jpg", "wb") as f:
        f.write(response.content)

if __name__ == "__main__":
    main()