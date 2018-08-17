import { environment } from '../../environments/environment';

export class Document {
    public constructor(public docType: string,
        public machineName: string,
        public humanName: string,
        public procedures: string[],
        public issues: string[],
        public problems: string[],
        public stageRelevance: string,
        public mainText?: string,
        public s3bucket?: string,
        public s3key?: string) { }

    public issuesFormatted(): string {
        return this.issues ? this.issues.map(issue => issue.trim()).join(', ') : '';
    }

    public s3url(): string {
        return environment.docBucketUrlBase + '/' + this.s3bucket + '/' + this.s3key;
    }
}

export const transformDoc = (doc: any): Document => {
    return new Document(doc.docType, doc.machineName, doc.humanName, doc.procedures, doc.issues, doc.problems,
        doc.stageRelevance, doc.mainText || doc.main_text, doc.s3bucket, doc.s3key);
};
