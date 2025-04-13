# Components
## Controllers
### VideoController:
This controller handles ***read (GET), update (PUT)*** and ***delete (DELETE)*** requests on **Video** entity

### VideoUploadController:
We upload a video and it transcodes into dash format, this controller only handles ***create (POST)***  requests  on **Video** entity.

### VideoSearchController:
This controller performs ***search*** requests on **Video** entity. It utilizes elasticsearch and can perform full-text queries.

## Services

### VideoService
- Carry out the ***create, read, update*** and ***delete*** operations on **Video** entity 
- This service is invoked by **VideoController**

### VideoUploadService
- Initiates transcoding on uploaded video file and can get status of the uploading task.
- This service is invoked by **VideoUploadController**

### VideoTranscodingService
- Handles the task initiated by **VideoUploadService**
- This service does the heavy lifting of **transcoding** the **uploaded video** to **dash** format.
- Updates the video url.

### VideoSearchService
- Performs different search operations using *title, description, category, difficultyLevel* and *materials used*
- This service is invoked by **VideoSearchController**

### VideoStorageService
- This service store the dash format video into S3 





